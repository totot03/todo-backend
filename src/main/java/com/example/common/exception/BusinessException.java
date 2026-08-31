package com.example.common.exception;

/**
 * 비즈니스 규칙 위반 시 던지는 공통 런타임 예외.
 *
 * <p>ErrorCode 하나로 HTTP 상태·코드·사용자 메시지가 함께 결정되므로, 서비스 계층은 {@code throw new
 * BusinessException(ErrorCode.XXX)} 형태로만 던지면 되고 GlobalExceptionHandler가 ApiResponse로 변환한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
