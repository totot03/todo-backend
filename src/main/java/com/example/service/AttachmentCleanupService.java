package com.example.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.entity.Attachment;
import com.example.repository.AttachmentRepository;
import com.example.repository.TodoRepository;
import com.example.service.storage.FileStorageService;

/**
 * 작성 중 업로드됐지만 끝내 어느 할 일에도 붙지 않은 이미지를 정리한다 (M7).
 *
 * <p>대상은 {@code todo_id IS NULL}이면서 {@code updated_at}이 {@value #RETENTION_HOURS}시간 이전인 첨부다. {@code
 * created_at}이 아니라 {@code updated_at} 기준인 이유는, 한동안 어느 할 일에 링크돼 있다가 본문 수정으로 다시 링크가 풀린 첨부의 유예 시간을
 * "링크가 풀린 시점"부터 다시 세기 위함이다 — 링크 해제도 {@code Attachment.unlink()}를 거쳐 저장되므로 {@code updated_at}이 갱신된다.
 *
 * <p>관리자 API는 범위 밖이다. 수동으로 실행해야 할 때는 이 서비스의 {@link #cleanupOrphans()}를 직접 호출한다(테스트가 하는 방식과 같다).
 */
@Service
public class AttachmentCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentCleanupService.class);

    private static final long RETENTION_HOURS = 24;

    private final AttachmentRepository attachmentRepository;
    private final TodoRepository todoRepository;
    private final FileStorageService fileStorageService;

    public AttachmentCleanupService(
            AttachmentRepository attachmentRepository,
            TodoRepository todoRepository,
            FileStorageService fileStorageService) {
        this.attachmentRepository = attachmentRepository;
        this.todoRepository = todoRepository;
        this.fileStorageService = fileStorageService;
    }

    /** 매일 새벽 3시 실행. 사용자 트래픽이 가장 적은 시간대라 정리 작업이 응답 지연에 끼치는 영향을 최소화한다. */
    @Scheduled(cron = "0 0 3 * * *")
    public void runScheduledCleanup() {
        cleanupOrphans();
    }

    /**
     * 고아 후보를 하나씩 검사해 삭제한다. 후보라도 <b>활성 Todo가 여전히 본문에서 참조 중이면 건너뛴다</b> — {@code todo_id}가 null인 상태에서도
     * 참조가 남아 있을 수 있는 이유는, {@code syncLinks}가 도는 순간과 이 스케줄러가 도는 순간 사이의 경합 때문이다 (예: 정리 대상 조회 직후 다른
     * 트랜잭션이 그 첨부를 새 Todo 본문에 붙이는 경우). 삭제 직전에 다시 한번 확인해 이런 경합을 좁힌다.
     *
     * <p>삭제는 {@code Attachment.markDeleted()}로 {@code deleted_at}만 기록한다 — Soft Delete 불변 규칙에 예외를
     * 만들지 않는다. 물리적으로 지우는 것은 디스크의 원본 파일(blob)뿐이다.
     *
     * @return 실제로 정리한 건수
     */
    @Transactional
    public int cleanupOrphans() {
        long startedAt = System.currentTimeMillis();
        LocalDateTime threshold = LocalDateTime.now().minusHours(RETENTION_HOURS);
        List<Attachment> candidates =
                attachmentRepository.findByTodoIsNullAndUpdatedAtBefore(threshold);

        int deletedCount = 0;
        for (Attachment candidate : candidates) {
            if (todoRepository.countActiveReferencing(candidate.getUuid().toString()) > 0) {
                continue;
            }
            deleteBlobBestEffort(candidate);
            candidate.markDeleted();
            deletedCount++;
        }

        log.info(
                "고아 파일 정리 완료: 후보 {}건 중 {}건 삭제, 소요 시간 {}ms",
                candidates.size(),
                deletedCount,
                System.currentTimeMillis() - startedAt);
        return deletedCount;
    }

    /**
     * blob 삭제 실패가 이 첨부 하나 때문에 나머지 후보 전체의 정리를 막으면 안 된다. 실패해도 DB 행은 그대로 soft delete하고 다음 후보로 넘어간다 —
     * 남은 blob은 디스크 용량 점검 시 별도로 확인한다.
     */
    private void deleteBlobBestEffort(Attachment candidate) {
        try {
            fileStorageService.delete(candidate.getStorageKey());
        } catch (RuntimeException e) {
            log.warn("고아 파일 blob 삭제에 실패했습니다. storageKey={}", candidate.getStorageKey(), e);
        }
    }
}
