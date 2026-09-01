package com.example.dto.todo;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.example.entity.Priority;

/**
 * 할 일 생성 요청 바디. API_SPEC.md 3.2.
 *
 * @param title 필수, 1~200자
 * @param description 선택, HTML 문자열. 저장 전 서버에서 sanitize한다(서비스 계층)
 * @param dueDate 선택, 과거 날짜도 허용한다(PRD 8.1 — 의도된 동작)
 * @param priority 선택. 생략 시 서비스 계층에서 {@link Priority#MEDIUM}으로 채운다
 */
public record TodoCreateRequest(
        @NotBlank(message = "제목을 입력해 주세요") @Size(max = 200, message = "제목은 200자 이하여야 합니다")
                String title,
        String description,
        LocalDate dueDate,
        Priority priority) {}
