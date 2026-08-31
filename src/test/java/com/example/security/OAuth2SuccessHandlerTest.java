package com.example.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import com.example.entity.Provider;
import com.example.entity.User;

/**
 * AccessTokenCookieWriter는 실제 인스턴스를 사용해 최종 Set-Cookie 헤더까지 통째로 검증한다 — JwtTokenProvider만 목킹해 토큰 발급과
 * 쿠키·리다이렉트 배선이 정확히 이어지는지 확인한다.
 */
class OAuth2SuccessHandlerTest {

    private static final String SUCCESS_REDIRECT_URI = "http://localhost:3000/oauth2/callback";

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final AccessTokenCookieWriter accessTokenCookieWriter = new AccessTokenCookieWriter();
    private final OAuth2SuccessHandler oAuth2SuccessHandler =
            new OAuth2SuccessHandler(
                    jwtTokenProvider, accessTokenCookieWriter, SUCCESS_REDIRECT_URI);

    @Test
    void issuesJwtCookieAndRedirectsToSuccessUri() throws Exception {
        User user =
                User.builder().id(1L).email("google@example.com").provider(Provider.GOOGLE).build();
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "google-uid"), "sub");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(jwtTokenProvider.createToken(1L)).thenReturn("dummy-token");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication);

        assertEquals(SUCCESS_REDIRECT_URI, response.getRedirectedUrl());
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookieHeader.startsWith("access_token=dummy-token"));
        assertTrue(setCookieHeader.contains("HttpOnly"));
        assertEquals(1, response.getHeaders(HttpHeaders.SET_COOKIE).size());
    }
}
