package com.example.common.response;

/**
 * 모든 API 응답을 감싸는 공통 래퍼. API_SPEC.md 1.3 형식을 그대로 따른다.
 *
 * <p>성공: {@code {success: true, data: T, error: null}} 실패: {@code {success: false, data: null,
 * error: ErrorResponse}}
 *
 * @param success 요청 처리 성공 여부
 * @param data 성공 시 반환할 데이터. 실패 시 null
 * @param error 실패 시 에러 정보. 성공 시 null
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    /** 성공 응답을 생성한다. */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 실패 응답을 생성한다. 실제 에러 코드 연동(GlobalExceptionHandler)은 M2-A에서 추가한다. */
    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
