package com.example.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 바디. API_SPEC.md 2.1 표의 제약·메시지를 그대로 따른다.
 *
 * @param email 필수, 이메일 형식
 * @param password 필수, 6자 이상(그 외 규칙 없음)
 * @param nickname 필수, 1~50자
 */
public record SignupRequest(
        @Email(message = "올바른 이메일 형식이 아닙니다") @NotBlank(message = "올바른 이메일 형식이 아닙니다") String email,
        @NotBlank(message = "비밀번호는 6자 이상이어야 합니다") @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다")
                String password,
        @NotBlank(message = "닉네임을 입력해 주세요") @Size(max = 50, message = "닉네임을 입력해 주세요")
                String nickname) {}
