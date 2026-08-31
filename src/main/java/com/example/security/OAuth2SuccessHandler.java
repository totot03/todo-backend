package com.example.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 구글 OAuth2 로그인 성공 시 자체 JWT를 발급해 httpOnly 쿠키로 내려주고 프론트로 리다이렉트한다.
 *
 * <p>{@link CustomOAuth2UserService}가 이미 계정 조회/연결을 끝내둔 {@link CustomOAuth2User}에서 userId만 꺼내 {@link
 * JwtTokenProvider#createToken(Long)}으로 토큰을 만들고, {@link AccessTokenCookieWriter}로 쿠키를 부착한다 — 이메일
 * 로그인(AuthController)과 완전히 동일한 두 컴포넌트를 재사용하므로 두 인증 경로의 쿠키 스펙이 갈라질 수 없다.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenCookieWriter accessTokenCookieWriter;
    private final String successRedirectUri;

    public OAuth2SuccessHandler(
            JwtTokenProvider jwtTokenProvider,
            AccessTokenCookieWriter accessTokenCookieWriter,
            @Value("${app.oauth2.success-redirect-uri}") String successRedirectUri) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.accessTokenCookieWriter = accessTokenCookieWriter;
        this.successRedirectUri = successRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        String token = jwtTokenProvider.createToken(principal.getUserId());
        accessTokenCookieWriter.attach(response, token);
        response.sendRedirect(successRedirectUri);
    }
}
