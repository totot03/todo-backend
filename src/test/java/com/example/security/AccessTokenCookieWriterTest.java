package com.example.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * attach/expire가 내려주는 Set-Cookie 헤더가 리팩터링 전 AuthController와 동일한 스펙(이름·httpOnly·path·maxAge)을 유지하는지
 * 검증한다.
 */
class AccessTokenCookieWriterTest {

    private final AccessTokenCookieWriter accessTokenCookieWriter = new AccessTokenCookieWriter();

    @Test
    void attachSetsHttpOnlyCookieWithTokenAndOneDayMaxAge() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessTokenCookieWriter.attach(response, "dummy-token");

        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookieHeader.startsWith("access_token=dummy-token"));
        assertTrue(setCookieHeader.contains("HttpOnly"));
        assertTrue(setCookieHeader.contains("Path=/"));
        assertTrue(setCookieHeader.contains("Max-Age=86400"));
    }

    @Test
    void expireSetsEmptyCookieWithZeroMaxAge() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessTokenCookieWriter.expire(response);

        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookieHeader.startsWith("access_token="));
        assertTrue(setCookieHeader.contains("HttpOnly"));
        assertTrue(setCookieHeader.contains("Max-Age=0"));
    }

    @Test
    void attachAddsExactlyOneSetCookieHeader() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessTokenCookieWriter.attach(response, "dummy-token");

        assertEquals(1, response.getHeaders(HttpHeaders.SET_COOKIE).size());
    }
}
