package com.example.dto.todo;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

import com.example.common.validation.NullOrNotBlank;
import com.example.entity.Priority;

/**
 * 할 일 부분 수정(PATCH) 요청 바디. API_SPEC.md 3.4.
 *
 * <p>보낸 필드만 반영하고, 보내지 않은(null) 필드는 기존 값을 유지한다 — 이 엔드포인트로는 필드를 명시적으로 "지우는"(null로 만드는) 것은 지원하지 않는다.
 * {@code completed}는 의도적으로 필드 자체가 없다 — 완료 상태는 반드시 {@code PATCH /api/todos/{id}/toggle} 전용 엔드포인트로만
 * 바꿀 수 있다(CLAUDE.md 불변 규칙).
 *
 * @param title 선택. 보내는 경우 공백 금지, 200자 이하
 * @param description 선택. 보내는 경우 저장 전 서버에서 sanitize한다
 * @param dueDate 선택
 * @param priority 선택
 */
public record TodoUpdateRequest(
        @NullOrNotBlank(message = "제목을 입력해 주세요") @Size(max = 200, message = "제목은 200자 이하여야 합니다")
                String title,
        String description,
        LocalDate dueDate,
        Priority priority) {}
