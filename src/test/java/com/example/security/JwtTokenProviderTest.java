package com.example.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** JwtTokenProvider의 생성·파싱·검증 왕복과 예외 흡수 동작을 검증한다. */
class JwtTokenProviderTest {

    // HS256 서명에 필요한 최소 256비트(32바이트)를 넘기기 위한 테스트 전용 시크릿.
    private static final String SECRET = "test-secret-key-for-jwt-must-be-long-enough-32bytes!!";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET, 86_400_000L);

    @Test
    void createTokenAndGetUserIdRoundTripsOriginalId() {
        String token = jwtTokenProvider.createToken(42L);

        Long userId = jwtTokenProvider.getUserId(token);

        assertEquals(42L, userId);
    }

    @Test
    void validateTokenReturnsTrueForFreshlyIssuedToken() {
        String token = jwtTokenProvider.createToken(1L);

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateTokenReturnsFalseForExpiredToken() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1000L);
        String expiredToken = expiredProvider.createToken(1L);

        assertFalse(jwtTokenProvider.validateToken(expiredToken));
    }

    @Test
    void validateTokenReturnsFalseForTamperedToken() {
        String token = jwtTokenProvider.createToken(1L);
        String tampered =
                token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertFalse(jwtTokenProvider.validateToken(tampered));
    }

    @Test
    void validateTokenReturnsFalseForMalformedToken() {
        assertFalse(jwtTokenProvider.validateToken("not-a-jwt"));
    }
}
