package com.example.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ErrorCode;
import com.example.common.sanitize.HtmlSanitizer;
import com.example.dto.file.ImageUploadResponse;
import com.example.entity.Attachment;
import com.example.entity.Todo;
import com.example.entity.User;
import com.example.repository.AttachmentRepository;
import com.example.service.storage.FileStorageService;
import com.example.service.storage.ImageType;
import com.example.service.storage.ImageTypeDetector;
import com.example.service.storage.StoredFile;

/**
 * 이미지 첨부 업로드·조회 (M7 / FEATURE_IMAGE_UPLOAD.md 3장).
 *
 * <p>조회는 {@link #findOwned}로 소유권을 검증한 뒤 처리하며, 없거나 타인 소유면 원인을 구분하지 않고 {@code FILE_NOT_FOUND}(404)로
 * 응답한다 — {@code TODO_NOT_FOUND}와 같은 이유다.
 */
@Service
public class AttachmentService {

    /** 원본 파일명 컬럼 길이. 넘치는 이름은 잘라 담는다 — 표시용이라 잘려도 기능이 깨지지 않는다. */
    private static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;

    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final ImageTypeDetector imageTypeDetector;
    private final long maxSize;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            FileStorageService fileStorageService,
            ImageTypeDetector imageTypeDetector,
            @Value("${file.storage.max-size}") long maxSize) {
        this.attachmentRepository = attachmentRepository;
        this.fileStorageService = fileStorageService;
        this.imageTypeDetector = imageTypeDetector;
        this.maxSize = maxSize;
    }

    /**
     * 업로드된 이미지를 저장한다. 크기 검증 → 형식 판정 → 파일 저장 → 레코드 생성 순이다.
     *
     * <p>파일명은 반드시 새 UUID로 만든다. 원본 파일명은 표시용으로만 저장하고 경로 생성에 쓰지 않는다.
     *
     * <p>크기 검증이 형식 판정보다 앞에 오는 이유는, 형식 판정이 파일 내용을 읽는 작업이라 거대한 파일을 먼저 걸러내는 편이 낫기 때문이다. multipart 컨테이너
     * 한도(6MB)는 이 검증(5MB)보다 크게 잡혀 있어, 5~6MB 파일도 여기까지 도달해 {@code FILE_TOO_LARGE}로 응답된다.
     *
     * <p>파일을 먼저 쓰고 레코드를 나중에 만들므로, INSERT가 실패하면 참조되지 않는 파일이 디스크에 남는다. UUID 키라 외부에서 접근할 수는 없지만 고아 정리도
     * DB를 기준으로 도는 이상 이 파일은 찾지 못한다 — 흔치 않은 경로라 감수하고, 저장소 용량 점검 시 확인한다.
     */
    @Transactional
    public ImageUploadResponse upload(MultipartFile file, User user) {
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        ImageType imageType = imageTypeDetector.detect(file);
        UUID uuid = UUID.randomUUID();
        StoredFile stored =
                fileStorageService.store(file, buildStorageKey(user.getId(), uuid, imageType));

        Attachment attachment =
                attachmentRepository.save(
                        Attachment.builder()
                                .uuid(uuid)
                                .user(user)
                                .storageType(stored.storageType())
                                .storageKey(stored.storageKey())
                                .originalFilename(displayName(file))
                                .contentType(imageType.getContentType())
                                .fileSize(stored.size())
                                .build());

        return ImageUploadResponse.from(attachment);
    }

    /** 소유권을 확인한 첨부를 돌려준다. 없거나 타인 소유면 구분 없이 {@code FILE_NOT_FOUND}다. */
    @Transactional(readOnly = true)
    public Attachment findOwned(UUID uuid, Long userId) {
        return attachmentRepository
                .findByUuidAndUserId(uuid, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
    }

    /**
     * 저장된 {@code todo}의 본문과 첨부 링크 상태를 맞춘다. {@code TodoService.create}/{@code update} 양쪽이 이 메서드 하나로
     * 모여야 두 경로가 갈라지지 않는다.
     *
     * <p>반드시 <b>sanitize를 마친 뒤의 HTML</b>을 넘겨야 한다 — sanitize 과정에서 제거될 {@code img}(외부 URL, {@code
     * javascript:} 등)까지 링크해 버리면 안 되기 때문이다.
     *
     * <p>{@code userId} 조건 없이 uuid만으로 첨부를 찾으면, 사용자 A가 본문에 남의 uuid를 적어 넣는 것만으로 B의 첨부 레코드를 자기 Todo에
     * 연결해 버릴 수 있다({@link AttachmentRepository#findByUserIdAndUuidIn}이 이 조건을 강제한다).
     *
     * <p>본문에서 빠진 이미지는 {@code todo_id}를 null로 되돌릴 뿐 물리 삭제하지 않는다 — 다시 본문에 넣으면 그대로 되살아나야 하고, 완전한 삭제
     * 여부는 고아 정리 스케줄러가 별도로 판단한다.
     */
    @Transactional
    public void syncLinks(Todo todo, String sanitizedHtml, Long userId) {
        Set<UUID> referencedUuids = extractReferencedUuids(sanitizedHtml);

        for (Attachment linked : attachmentRepository.findByTodoId(todo.getId())) {
            if (!referencedUuids.contains(linked.getUuid())) {
                linked.unlink();
            }
        }

        if (!referencedUuids.isEmpty()) {
            List<Attachment> toLink =
                    attachmentRepository.findByUserIdAndUuidIn(userId, referencedUuids);
            for (Attachment attachment : toLink) {
                attachment.linkTo(todo);
            }
        }
    }

    /**
     * sanitize를 마친 본문에서 {@code img[src]}가 가리키는 첨부 uuid만 모은다. {@link
     * HtmlSanitizer#IMAGE_SRC_PATTERN}을 그대로 재사용해, "sanitizer가 살려 둔 img"와 "syncLinks가 참조로 인정하는 img"의
     * 판정 기준이 어긋나지 않게 한다.
     */
    private Set<UUID> extractReferencedUuids(String sanitizedHtml) {
        if (!StringUtils.hasText(sanitizedHtml)) {
            return Set.of();
        }
        Elements images = Jsoup.parseBodyFragment(sanitizedHtml).body().select("img");
        Set<UUID> uuids = new HashSet<>();
        for (Element img : images) {
            Matcher matcher = HtmlSanitizer.IMAGE_SRC_PATTERN.matcher(img.attr("src"));
            if (matcher.matches()) {
                uuids.add(UUID.fromString(matcher.group(1)));
            }
        }
        return uuids;
    }

    /** 저장 경로. 사용자별·연월별로 나누는 이유는 한 디렉터리에 파일이 무한정 쌓이면 파일시스템 조회가 느려지고 백업·정리 단위를 잡기 어려워지기 때문이다. */
    private String buildStorageKey(Long userId, UUID uuid, ImageType imageType) {
        LocalDate today = LocalDate.now();
        return "todos/%d/%04d/%02d/%s.%s"
                .formatted(
                        userId,
                        today.getYear(),
                        today.getMonthValue(),
                        uuid,
                        imageType.getExtension());
    }

    /**
     * 표시용 원본 파일명. 경로 구분자가 섞여 들어와도 마지막 구간만 남기고, 컬럼 길이를 넘으면 잘라 담는다.
     *
     * <p>{@code /}와 {@code \} 둘 다 구분자로 본다 — {@link StringUtils#getFilename}은 {@code /}만 잘라내는데, 옛
     * Windows 브라우저는 {@code originalFilename}에 {@code C:\Users\...\사진.png} 같은 전체 경로를 그대로 실어 보냈다. 그
     * 값을 그대로 표시하면 사용자의 로컬 디렉터리 구조가 화면에 노출된다.
     *
     * <p>경로 생성에는 쓰지 않으므로 여기서 걸러내지 못해도 보안 취약점은 아니지만, 표시 정보로서는 걸러 두는 편이 낫다.
     */
    private String displayName(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            return null;
        }
        String lastSegment = StringUtils.getFilename(original.replace('\\', '/'));
        return lastSegment.length() > MAX_ORIGINAL_FILENAME_LENGTH
                ? lastSegment.substring(0, MAX_ORIGINAL_FILENAME_LENGTH)
                : lastSegment;
    }
}
