package com.example.dto.todo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.entity.Todo;

/**
 * 할 일 응답. API_SPEC.md 3.1·3.2·3.3·3.4.
 *
 * <p>Todo 엔티티를 컨트롤러 응답에 직접 노출하지 않기 위한 변환 대상이다. {@code user}/{@code userId} 필드는 두지 않는다 — API_SPEC 응답
 * 예시에도 없고, 참조 시 LAZY 연관({@code Todo.user})을 불필요하게 초기화하게 되기 때문이다.
 */
public record TodoResponse(
        Long id,
        String title,
        String description,
        LocalDate dueDate,
        String priority,
        boolean completed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getDescription(),
                todo.getDueDate(),
                todo.getPriority().name(),
                todo.isCompleted(),
                todo.getCreatedAt(),
                todo.getUpdatedAt());
    }
}
