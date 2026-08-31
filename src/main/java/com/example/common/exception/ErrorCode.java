package com.example.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 프로젝트 전역에서 사용하는 고정 에러 코드 6종. CLAUDE.md 불변 규칙에 정의된 값이며 임의로 추가하지 않는다.
 *
 * <p>LOGIN_FAILED(계정 없음/비밀번호 불일치)와 TODO_NOT_FOUND(리소스 없음/소유권 불일치)는 의도적으로 원인을 구분하지 않는다 — 열거
 * 공격(enumeration attack)을 막기 위함이다.
 */
public enum ErrorCode {
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    EMAIL_DUPLICATED("EMAIL_DUPLICATED", HttpStatus.BAD_REQUEST, "이미 사용 중인 이메일입니다."),
    LOGIN_FAILED("LOGIN_FAILED", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TODO_NOT_FOUND("TODO_NOT_FOUND", HttpStatus.NOT_FOUND, "할 일을 찾을 수 없습니다."),
    INTERNAL_ERROR("INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
