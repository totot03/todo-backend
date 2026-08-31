package com.example.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 생성·파싱·검증을 담당한다.
 *
 * <p>subject는 {@code User.id}(Long → String)를 사용한다. Access Token만 발급하며 Refresh Token은 두지 않는다(PRD.md
 * §8 불변 규칙). 토큰은 오직 httpOnly 쿠키로만 전달되고 응답 본문에는 절대 담기지 않는다 — 이 클래스는 순수 인코딩/디코딩만 담당하고 쿠키 배선은
 * AuthController·JwtAuthenticationFilter의 몫이다.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /** userId를 subject로 담은 JWT를 발급한다. */
    public String createToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** 토큰에서 userId를 꺼낸다. 호출 전 {@link #validateToken(String)}으로 유효성을 먼저 확인해야 한다. */
    public Long getUserId(String token) {
        String subject =
                Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
                        .getSubject();
        return Long.valueOf(subject);
    }

    /**
     * 토큰이 유효한지 검사한다. 만료(ExpiredJwtException)·서명 불일치(SignatureException)·형식 오류
     * (MalformedJwtException) 등 {@link JwtException} 계열과 빈 토큰({@link IllegalArgumentException})은 모두
     * false로 흡수하고, 예외 상세를 호출부로 전파하지 않는다.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
