package com.example.common.response;

/**
 * 입력값 검증 실패(400) 시 필드별 에러를 표현한다. API_SPEC.md 1.3의 {@code error.fieldErrors} 항목.
 *
 * @param field 검증에 실패한 필드명
 * @param message 사용자에게 보여줄 한국어 에러 메시지
 */
public record FieldError(String field, String message) {}
