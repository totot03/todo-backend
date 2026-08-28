package com.example.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.entity.Todo;

/**
 * {@link Todo} 리포지토리.
 *
 * <p>소유권 검증(userId 일치 확인)은 {@link #findByIdAndUserId}로 처리한다. 불일치 시 서비스 계층에서 {@code
 * TODO_NOT_FOUND}(404)로 응답한다 — 403이 아니다 (리소스 존재 여부 비노출, API_SPEC.md). 페이지네이션·필터·정렬 쿼리는 M3에서 추가한다.
 */
public interface TodoRepository extends JpaRepository<Todo, Long> {

    Optional<Todo> findByIdAndUserId(Long id, Long userId);
}
