package com.example.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/** 예외 종류와 무관하게 항상 동일한 failure-redirect-uri로 보내는지 확인한다(열거 공격 방지 원칙). */
class OAuth2FailureHandlerTest {

    private static final String FAILURE_REDIRECT_URI = "http://localhost:3000/login?error=oauth";

    private final OAuth2FailureHandler oAuth2FailureHandler =
            new OAuth2FailureHandler(FAILURE_REDIRECT_URI);

    @Test
    void redirectsToFailureUriForOAuth2AuthenticationException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        oAuth2FailureHandler.onAuthenticationFailure(
                request, response, new OAuth2AuthenticationException("access_denied"));

        assertEquals(FAILURE_REDIRECT_URI, response.getRedirectedUrl());
    }

    @Test
    void redirectsToSameFailureUriForDifferentExceptionSubtype() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        oAuth2FailureHandler.onAuthenticationFailure(
                request, response, new InsufficientAuthenticationException("인증 정보 부족"));

        assertEquals(FAILURE_REDIRECT_URI, response.getRedirectedUrl());
    }
}
