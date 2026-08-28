package com.example.common.response;

import java.util.List;

/**
 * API 실패 응답의 error 필드. API_SPEC.md 1.3, 1.5 형식을 따른다.
 *
 * <p>고정 에러 코드 6종(VALIDATION_FAILED, EMAIL_DUPLICATED, LOGIN_FAILED, UNAUTHORIZED, TODO_NOT_FOUND,
 * INTERNAL_ERROR) 정의와 GlobalExceptionHandler 연동은 M2-A에서 추가한다.
 *
 * @param code 고정 에러 코드
 * @param message 사용자에게 그대로 보여줄 한국어 문장. 스택트레이스·SQL 등 내부 정보를 담지 않는다
 * @param fieldErrors 400 검증 실패일 때만 채워지고, 그 외에는 null
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {}
