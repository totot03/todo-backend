package com.example.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS 설정. 프론트엔드 개발 서버(localhost:3000)만 명시적으로 허용한다.
 *
 * <p>httpOnly 쿠키로 인증 정보를 주고받으려면 {@code allowCredentials(true)}가 필수인데, 이 옵션이 켜지면 스펙상 {@code
 * allowedOrigins}에 와일드카드({@code *})를 쓸 수 없다 — CLAUDE.md 불변 규칙(CORS 와일드카드 금지)이 사실 브라우저 스펙의 제약과 일치한다.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
