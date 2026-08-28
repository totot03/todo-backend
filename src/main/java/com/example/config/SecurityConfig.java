package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * M0 최소 스코프 Security 설정.
 *
 * <p>spring-boot-starter-security 의존성이 클래스패스에 있으면 별도 설정 없이도 모든 요청에 인증을 요구하는 기본 정책이 자동 적용된다. 지금은
 * 헬스체크만 열어두고, STATELESS 세션·JWT 인증 필터·OAuth2 경로·CSRF 비활성화 등 완전한 설정은 M2-A에서 이 클래스를 교체하며 추가한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                auth ->
                        auth.requestMatchers("/api/health")
                                .permitAll()
                                .anyRequest()
                                .authenticated());
        return http.build();
    }
}
