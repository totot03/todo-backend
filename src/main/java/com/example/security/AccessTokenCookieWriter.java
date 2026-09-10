package com.example.security;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * {@code access_token} httpOnly 쿠키를 발급·만료시키는 공유 컴포넌트.
 *
 * <p>{@link com.example.controller.AuthController}(이메일 로그인)와 구글 OAuth2 로그인(M2-B) 성공 핸들러가 서로 다른 경로에서
 * 동일한 스펙(이름·httpOnly·path·maxAge)의 쿠키를 내려줘야 한다 — 인증은 httpOnly 쿠키 전용이고 두 경로의 쿠키 스펙이 갈라지면 안 된다는
 * CLAUDE.md 불변 규칙 때문이다. 원래 AuthController에 private으로 있던 쿠키 배선 로직을 이 클래스로 추출해 두 곳이 항상 같은 스펙을 쓰도록
 * 강제한다.
 *
 * <p>{@code secure}/{@code sameSite}는 환경변수로 뺐다 — 프론트(Amplify)와 백엔드(CloudFront)가 서로 다른 도메인인 운영에서는
 * cross-site 쿠키 전송에 {@code Secure; SameSite=None}이 필수지만, 로컬 개발(http://localhost)에서 {@code Secure}를
 * 켜면 브라우저가 쿠키 자체를 저장하지 않는다. 기본값은 로컬 기준(비보안·Lax)이고 운영은 application-prod.properties가 덮어쓴다.
 */
@Component
public class AccessTokenCookieWriter {

    private static final String COOKIE_NAME = "access_token";
    private static final long COOKIE_MAX_AGE_SECONDS = 86_400L;

    private final boolean secure;
    private final String sameSite;

    public AccessTokenCookieWriter(
            @Value("${app.cookie.secure:false}") boolean secure,
            @Value("${app.cookie.same-site:Lax}") String sameSite) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    /** 로그인·회원가입·OAuth2 로그인 성공 시 access_token 쿠키를 응답에 부착한다. */
    public void attach(HttpServletResponse response, String token) {
        ResponseCookie cookie =
                ResponseCookie.from(COOKIE_NAME, token)
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite(sameSite)
                        .path("/")
                        .maxAge(COOKIE_MAX_AGE_SECONDS)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 로그아웃 시 쿠키를 즉시 만료시킨다. */
    public void expire(HttpServletResponse response) {
        ResponseCookie expiredCookie =
                ResponseCookie.from(COOKIE_NAME, "")
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite(sameSite)
                        .path("/")
                        .maxAge(0)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }
}
