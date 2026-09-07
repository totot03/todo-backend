package com.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ErrorCode;
import com.example.dto.file.ImageUploadResponse;
import com.example.entity.Attachment;
import com.example.entity.StorageType;
import com.example.entity.Todo;
import com.example.entity.User;
import com.example.repository.AttachmentRepository;
import com.example.service.storage.FileStorageService;
import com.example.service.storage.ImageTypeDetector;
import com.example.service.storage.StoredFile;

/** AttachmentService의 업로드 검증과 저장 키 생성 규칙을 확인한다 (FR-T14). */
class AttachmentServiceTest {

    private static final long MAX_SIZE = 5 * 1024 * 1024;

    /** 최소한의 유효한 PNG 헤더. 매직 바이트 판정만 통과하면 되므로 실제 이미지 데이터는 필요 없다. */
    private static final byte[] PNG_BYTES = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final AttachmentService attachmentService =
            new AttachmentService(
                    attachmentRepository, fileStorageService, new ImageTypeDetector(), MAX_SIZE);

    private final User owner = User.builder().id(7L).build();

    @Test
    void rejectsFileLargerThanMaxSizeBeforeTouchingStorage() {
        MockMultipartFile huge = new MockMultipartFile("file", new byte[(int) MAX_SIZE + 1]);

        BusinessException e =
                assertThrows(BusinessException.class, () -> attachmentService.upload(huge, owner));

        assertEquals(ErrorCode.FILE_TOO_LARGE, e.getErrorCode());
        verify(fileStorageService, never()).store(any(), anyString());
    }

    @Test
    void rejectsDisguisedFileBeforeTouchingStorage() {
        MockMultipartFile disguised =
                new MockMultipartFile("file", "위장.png", "image/png", "그냥 텍스트".getBytes());

        BusinessException e =
                assertThrows(
                        BusinessException.class, () -> attachmentService.upload(disguised, owner));

        assertEquals(ErrorCode.INVALID_FILE_TYPE, e.getErrorCode());
        verify(fileStorageService, never()).store(any(), anyString());
    }

    @Test
    void buildsStorageKeyFromServerSideValuesOnly() {
        LocalDate today = LocalDate.now();
        String expectedPrefix =
                "todos/7/%04d/%02d/".formatted(today.getYear(), today.getMonthValue());
        MockMultipartFile file =
                new MockMultipartFile("file", "../../악성.php", "application/x-php", PNG_BYTES);
        stubStore();

        attachmentService.upload(file, owner);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).store(any(), key.capture());
        String storageKey = key.getValue();

        assertTrue(storageKey.startsWith(expectedPrefix), storageKey);
        // 확장자는 매직 바이트 판정 결과(png)를 따르고, 원본 파일명은 경로 어디에도 나타나지 않는다.
        assertTrue(storageKey.endsWith(".png"), storageKey);
        assertFalse(storageKey.contains("악성"), storageKey);
        assertFalse(storageKey.contains(".."), storageKey);
    }

    @Test
    void storesServerDeterminedContentTypeAndKeepsOriginalNameForDisplayOnly() {
        MockMultipartFile file =
                new MockMultipartFile("file", "C:\\사진\\스크린샷.php", "application/x-php", PNG_BYTES);
        stubStore();

        attachmentService.upload(file, owner);

        ArgumentCaptor<Attachment> saved = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(saved.capture());

        // 클라이언트가 보낸 Content-Type(application/x-php)이 아니라 판정 결과가 저장돼야 한다.
        assertEquals("image/png", saved.getValue().getContentType());
        // 표시용 이름은 경로 구분자를 걷어낸 마지막 구간만 남는다.
        assertEquals("스크린샷.php", saved.getValue().getOriginalFilename());
    }

    @Test
    void responseUrlIsBuiltFromUuidNotStorageKey() {
        MockMultipartFile file = new MockMultipartFile("file", "그림.png", "image/png", PNG_BYTES);
        stubStore();

        ImageUploadResponse response = attachmentService.upload(file, owner);

        assertEquals("/api/files/" + response.uuid(), response.url());
        assertEquals("그림.png", response.filename());
        assertEquals(PNG_BYTES.length, response.size());
    }

    @Test
    void missingOrOtherUsersFileIsFileNotFound() {
        UUID uuid = UUID.randomUUID();
        when(attachmentRepository.findByUuidAndUserId(uuid, 7L)).thenReturn(Optional.empty());

        BusinessException e =
                assertThrows(BusinessException.class, () -> attachmentService.findOwned(uuid, 7L));

        assertEquals(ErrorCode.FILE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void syncLinksAttachesImageReferencedInSanitizedBody() {
        Todo todo = Todo.builder().id(100L).build();
        UUID uuid = UUID.randomUUID();
        Attachment attachment = Attachment.builder().uuid(uuid).build();
        when(attachmentRepository.findByTodoId(100L)).thenReturn(List.of());
        when(attachmentRepository.findByUserIdAndUuidIn(eq(7L), anyCollection()))
                .thenReturn(List.of(attachment));

        attachmentService.syncLinks(todo, "<p><img src=\"/api/files/" + uuid + "\"></p>", 7L);

        assertEquals(todo, attachment.getTodo());
    }

    @Test
    void syncLinksUnlinksAttachmentNoLongerReferenced() {
        Todo todo = Todo.builder().id(100L).build();
        Attachment stillLinked = Attachment.builder().uuid(UUID.randomUUID()).todo(todo).build();
        when(attachmentRepository.findByTodoId(100L)).thenReturn(List.of(stillLinked));

        // 본문에 img가 하나도 없다 — 기존에 링크돼 있던 첨부는 전부 떨어져 나가야 한다.
        attachmentService.syncLinks(todo, "<p>이미지를 뺐습니다</p>", 7L);

        assertNull(stillLinked.getTodo());
        // 참조가 없으므로 링크 대상 조회 자체를 하지 않는다.
        verify(attachmentRepository, never()).findByUserIdAndUuidIn(anyLong(), anyCollection());
    }

    @Test
    void syncLinksKeepsStillReferencedAttachmentLinked() {
        Todo todo = Todo.builder().id(100L).build();
        UUID uuid = UUID.randomUUID();
        Attachment attachment = Attachment.builder().uuid(uuid).todo(todo).build();
        when(attachmentRepository.findByTodoId(100L)).thenReturn(List.of(attachment));
        when(attachmentRepository.findByUserIdAndUuidIn(eq(7L), anyCollection()))
                .thenReturn(List.of(attachment));

        attachmentService.syncLinks(todo, "<img src=\"/api/files/" + uuid + "\">", 7L);

        assertEquals(todo, attachment.getTodo());
    }

    @Test
    void syncLinksAlwaysScopesLinkLookupToOwner() {
        // 본문에 남의 uuid를 적어 넣더라도, 링크 대상 조회 자체가 userId로 스코프돼 있어(리포지토리 쿼리 조건) 다른 사용자의
        // 첨부를 가져올 수 없다. 서비스가 그 userId 인자를 실제로 넘기는지를 고정한다.
        Todo todo = Todo.builder().id(100L).build();
        UUID othersUuid = UUID.randomUUID();
        when(attachmentRepository.findByTodoId(100L)).thenReturn(List.of());
        when(attachmentRepository.findByUserIdAndUuidIn(anyLong(), anyCollection()))
                .thenReturn(List.of());

        attachmentService.syncLinks(todo, "<img src=\"/api/files/" + othersUuid + "\">", 7L);

        verify(attachmentRepository).findByUserIdAndUuidIn(eq(7L), anyCollection());
    }

    @Test
    void syncLinksWithBlankBodyUnlinksWithoutQueryingLinkTargets() {
        Todo todo = Todo.builder().id(100L).build();
        Attachment linked = Attachment.builder().uuid(UUID.randomUUID()).todo(todo).build();
        when(attachmentRepository.findByTodoId(100L)).thenReturn(List.of(linked));

        attachmentService.syncLinks(todo, null, 7L);

        assertNull(linked.getTodo());
        verify(attachmentRepository, never()).findByUserIdAndUuidIn(anyLong(), anyCollection());
    }

    /** store는 넘겨받은 키를 그대로 담아 돌려주고, save는 넘겨받은 엔티티를 그대로 돌려준다. */
    private void stubStore() {
        when(fileStorageService.store(any(), anyString()))
                .thenAnswer(
                        invocation ->
                                new StoredFile(
                                        StorageType.LOCAL,
                                        invocation.getArgument(1),
                                        PNG_BYTES.length));
        when(attachmentRepository.save(any(Attachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
