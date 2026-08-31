package com.example.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 바디. API_SPEC.md 2.2.
 *
 * <p>여기서 걸리는 검증 실패는 400 VALIDATION_FAILED다. 이메일·비밀번호 형식은 맞지만 자격 증명이 틀린 경우는 이 DTO가 아니라 AuthService가
 * 401 LOGIN_FAILED로 통일해 처리한다 — 계정 존재 여부가 새지 않도록 하기 위함이다.
 *
 * @param email 필수, 이메일 형식
 * @param password 필수 입력
 */
public record LoginRequest(
        @Email(message = "올바른 이메일 형식이 아닙니다") @NotBlank(message = "올바른 이메일 형식이 아닙니다") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요") String password) {}
