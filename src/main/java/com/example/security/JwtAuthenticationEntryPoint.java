package com.example.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.example.common.response.ApiResponse;
import com.example.common.response.ErrorResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * 인증되지 않은 요청이 보호된 경로에 접근할 때 401을 기존 ApiResponse JSON 포맷으로 직접 응답한다.
 *
 * <p>필터 체인(서블릿 레벨)에서 발생하는 인증 실패는 {@code @RestControllerAdvice}인 GlobalExceptionHandler가 잡지 못한다 —
 * DispatcherServlet에 도달하기 전 단계이기 때문이다. 그래서 이 클래스가 직접 응답 바디를 작성한다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> body =
                ApiResponse.error(new ErrorResponse("UNAUTHORIZED", "인증이 필요합니다", null));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
