package com.example.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * 구글 OAuth2 로그인 실패(동의 취소, 프로필 조회 실패 등) 시 로그인 페이지로 되돌려보낸다.
 *
 * <p>실패 원인을 구분해 응답에 노출하지 않는다 — LOGIN_FAILED/TODO_NOT_FOUND가 원인을 구분하지 않는 것과 같은 열거 공격 방지 원칙
 * (CLAUDE.md 불변 규칙)을 OAuth 실패에도 동일하게 적용한 것이다. 이 경로는 JSON이 아닌 브라우저 리다이렉트이므로 {@code ErrorCode}(고정 6종)
 * 체계와 무관하며, 신규 ErrorCode를 추가하지 않는다. 예외 상세는 서버 로그에만 남긴다.
 */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2FailureHandler.class);

    private final String failureRedirectUri;

    public OAuth2FailureHandler(
            @Value("${app.oauth2.failure-redirect-uri}") String failureRedirectUri) {
        this.failureRedirectUri = failureRedirectUri;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        log.warn("구글 OAuth2 로그인 실패: {}", exception.getMessage());
        response.sendRedirect(failureRedirectUri);
    }
}
