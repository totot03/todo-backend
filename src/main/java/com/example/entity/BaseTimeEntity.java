package com.example.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Getter;

/**
 * 모든 엔티티가 공통으로 갖는 시간 관련 필드를 정의한다 (PRD 8.2).
 *
 * <p>{@code createdAt}/{@code updatedAt}은 JPA Auditing({@code @EnableJpaAuditing})이 자동으로 채운다.
 * {@code deletedAt}은 Auditing 대상이 아니며 {@link #markDeleted()} 호출로만 채워진다 — Soft Delete 조회
 * 필터({@code @SQLRestriction("deleted_at IS NULL")})는 각 엔티티에서 개별 적용한다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column private LocalDateTime deletedAt;

    /**
     * 소프트 삭제 시각을 현재 시각으로 기록한다.
     *
     * <p>실제 삭제 처리(M3 Todo 삭제 등)는 서비스 계층에서 이 메서드를 호출해 사용한다.
     */
    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
    }
}
