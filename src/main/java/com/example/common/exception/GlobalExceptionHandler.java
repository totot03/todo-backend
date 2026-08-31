package com.example.common.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.common.response.ApiResponse;
import com.example.common.response.ErrorResponse;
import com.example.common.response.FieldError;

/**
 * 전역 예외 처리기. 모든 예외를 기존 {@link ApiResponse}/{@link ErrorResponse} 포맷으로 변환해 API 응답 형태를 하나로 통일한다.
 *
 * <p>{@code org.springframework.validation.FieldError}와 이 프로젝트의 {@link FieldError}는 이름이 같다.
 * validationException 처리에서는 스트림 매핑의 람다 인자 타입 추론에 맡겨 스프링 쪽 클래스명을 코드에 직접 등장시키지 않음으로써 충돌을 피한다.
 *
 * <p>필터 체인에서 발생하는 인증 실패(401)는 이 클래스가 아니라 별도의 AuthenticationEntryPoint가 처리한다 —
 * {@code @RestControllerAdvice}는 DispatcherServlet 밖에서 던져지는 예외를 잡지 못하기 때문이다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(
                        ApiResponse.error(
                                new ErrorResponse(
                                        errorCode.getCode(), errorCode.getMessage(), null)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors =
                e.getBindingResult().getFieldErrors().stream()
                        .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                        .toList();
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(errorCode.getStatus())
                .body(
                        ApiResponse.error(
                                new ErrorResponse(
                                        errorCode.getCode(), errorCode.getMessage(), fieldErrors)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("예상하지 못한 서버 오류가 발생했습니다.", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
                .body(
                        ApiResponse.error(
                                new ErrorResponse(
                                        errorCode.getCode(), errorCode.getMessage(), null)));
    }
}
