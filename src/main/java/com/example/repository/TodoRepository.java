package com.example.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.entity.Todo;

/**
 * {@link Todo} 리포지토리.
 *
 * <p>소유권 검증(userId 일치 확인)은 {@link #findByIdAndUserId}로 처리한다. 불일치 시 서비스 계층에서 {@code
 * TODO_NOT_FOUND}(404)로 응답한다 — 403이 아니다 (리소스 존재 여부 비노출, API_SPEC.md).
 *
 * <p>{@link Todo}에 걸린 {@code @SQLRestriction("deleted_at IS NULL")}이 이 인터페이스의 모든 쿼리(파생
 * 쿼리·{@code @Query} 모두)에 자동 적용되므로, 아래 쿼리들에는 {@code deleted_at} 조건을 별도로 쓰지 않는다.
 */
public interface TodoRepository extends JpaRepository<Todo, Long> {

    Optional<Todo> findByIdAndUserId(Long id, Long userId);

    /**
     * 목록 조회용 검색 쿼리. {@code completed}·{@code keyword}는 둘 다 optional 필터다.
     *
     * <p>필터 값 자체를 null로 바인딩해 {@code :param IS NULL OR ...} 패턴으로 처리하지 않는다 — PostgreSQL이 null 파라미터의
     * 타입을 제대로 추론하지 못해 {@code lower(bytea)}/{@code bytea를 boolean으로 변환 불가} 같은 오류(Hibernate 7 +
     * pgjdbc의 알려진 문제, SQLState 42883/42846)가 나기 때문이다. 대신 "필터를 적용할지 여부"를 항상 non-null인 {@code
     * boolean} 플래그로 별도로 넘겨({@code hasXxxFilter}) 애초에 null 파라미터 바인딩 자체를 피한다. 값 파라미터({@code
     * completedValue}/{@code keywordValue})는 플래그가 false일 때는 사용되지 않지만 항상 구체적인 타입의 값이 전달되므로 타입 추론 문제가
     * 발생하지 않는다.
     */
    @Query(
            "SELECT t FROM Todo t "
                    + "WHERE t.user.id = :userId "
                    + "AND (:hasCompletedFilter = false OR t.completed = :completedValue) "
                    + "AND (:hasKeywordFilter = false "
                    + "     OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keywordValue, '%')) "
                    + "     OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keywordValue, '%')))")
    Page<Todo> search(
            @Param("userId") Long userId,
            @Param("hasCompletedFilter") boolean hasCompletedFilter,
            @Param("completedValue") boolean completedValue,
            @Param("hasKeywordFilter") boolean hasKeywordFilter,
            @Param("keywordValue") String keywordValue,
            Pageable pageable);

    /**
     * 고아 파일 정리 스케줄러가 삭제 직전에 거치는 가드다 ({@code AttachmentCleanupService}). 이 결과가 0이어야 실제로 지운다.
     *
     * <p>{@code deleted_at} 조건을 따로 쓰지 않아도 되는 이유는 {@link Todo}의 {@code @SQLRestriction}이 이 쿼리에도 자동
     * 적용되기 때문이다 — 즉 이 카운트는 이미 "소프트 삭제되지 않은(활성) Todo 중에서"라는 의미다. 소프트 삭제된 Todo가 본문에 같은 uuid를 갖고
     * 있어도(복구 가능성이 있어 첨부 자체는 남겨 둬야 하지만) 이 카운트에는 잡히지 않는다는 뜻이므로, 정리 대상 선정에서 {@code todo_id IS NULL}(작성
     * 중 업로드) 후보만 다루는 것과 결합해야 의미가 맞는다 — 소프트 삭제된 Todo에 여전히 링크된 첨부는 {@code todo_id}가 non-null이라 애초에 정리
     * 후보에 오르지 않는다.
     */
    @Query("SELECT COUNT(t) FROM Todo t WHERE t.description LIKE CONCAT('%', :uuid, '%')")
    long countActiveReferencing(@Param("uuid") String uuid);
}
