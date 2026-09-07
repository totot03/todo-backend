package com.example.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.entity.Attachment;

/**
 * {@link Attachment} 리포지토리.
 *
 * <p>{@link Attachment}에 걸린 {@code @SQLRestriction("deleted_at IS NULL")}이 아래 모든 쿼리에 자동 적용되므로
 * {@code deleted_at} 조건을 따로 쓰지 않는다. 고아 정리가 소프트 삭제한 첨부를 다음 주기에 다시 집어들지 않는 것도 이 필터 덕분이다.
 *
 * <p>소유권 검증은 {@link #findByUuidAndUserId}로 처리하고, 불일치 시 서비스 계층에서 {@code FILE_NOT_FOUND}(404)로 응답한다 —
 * 403이 아니다 (파일 존재 여부 비노출, API_SPEC.md).
 */
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /** 단건 조회 겸 소유권 검증. {@code GET /api/files/{uuid}}가 쓴다. */
    Optional<Attachment> findByUuidAndUserId(UUID uuid, Long userId);

    /**
     * 본문에서 추출한 UUID 목록에 해당하는 내 첨부들을 가져온다. 링크 갱신용이다.
     *
     * <p>{@code userId} 조건이 함께 걸려 있어야 남의 파일 UUID를 본문에 적어 넣는 방식으로 링크를 가로챌 수 없다.
     */
    List<Attachment> findByUserIdAndUuidIn(Long userId, Collection<UUID> uuids);

    /** 특정 할 일에 현재 붙어 있는 첨부들. 링크 해제 대상을 찾을 때 쓴다. */
    List<Attachment> findByTodoId(Long todoId);

    /**
     * 고아 후보. 어느 할 일에도 붙지 않은 채 {@code threshold} 이전에 마지막으로 변경된 첨부들이다.
     *
     * <p>{@code createdAt}이 아니라 {@code updatedAt} 기준인 이유는, 링크가 붙었다가 본문 수정으로 다시 떨어진 첨부의 유예 시간을 링크 해제
     * 시점부터 다시 세기 위함이다.
     */
    List<Attachment> findByTodoIsNullAndUpdatedAtBefore(LocalDateTime threshold);
}
