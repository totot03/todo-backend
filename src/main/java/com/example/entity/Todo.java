package com.example.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLRestriction;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 할 일 (PRD 8.2). */
@Entity
@Table(name = "todos")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Todo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유자. 목록 조회 성능을 위해 지연 로딩한다 (NFR-P02). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    /** 서식 있는 HTML 설명. 저장 전 sanitize는 M3 서비스 계층에서 처리한다. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 마감일. 과거 날짜도 허용한다 (PRD 8.2 — 의도된 동작). */
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    @Column(nullable = false)
    private boolean completed;

    /**
     * 부분 수정(PATCH)의 병합 로직은 서비스 계층이 계산해서 최종 값을 넘기고, 이 메서드는 그 값으로 필드를 교체하기만 한다 — 엔티티가 "어떤 필드가 생략됐는지"
     * 같은 요청 표현 방식을 알 필요가 없게 하기 위함이다. description은 호출 전에 이미 sanitize된 값이어야 한다.
     */
    public void update(String title, String description, LocalDate dueDate, Priority priority) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.priority = priority;
    }

    /** 완료 여부를 반전한다. {@code PATCH /api/todos/{id}/toggle} 전용 — 다른 경로로는 completed를 바꾸지 않는다. */
    public void toggleComplete() {
        this.completed = !this.completed;
    }
}
