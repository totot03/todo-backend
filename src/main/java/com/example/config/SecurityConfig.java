package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.example.repository.UserRepository;
import com.example.security.CustomOAuth2UserService;
import com.example.security.JwtAuthenticationEntryPoint;
import com.example.security.JwtAuthenticationFilter;
import com.example.security.JwtTokenProvider;
import com.example.security.OAuth2FailureHandler;
import com.example.security.OAuth2SuccessHandler;

/**
 * M2-A 자체 인증과 M2-B 구글 OAuth2 인증 파이프라인을 함께 설정한다. M0의 최소 골격(헬스체크만 permitAll)을 완전히 대체한다.
 *
 * <p>세션을 쓰지 않는 STATELESS 정책과 JWT 쿠키 필터를 조합한다. 서버가 세션 상태를 들고 있지 않아 CSRF 토큰 검증이 의미가 없으므로 CSRF는
 * 비활성화한다(REST API 표준 패턴). 구글 로그인도 최종적으로는 {@link OAuth2SuccessHandler}가 같은 JWT 쿠키를 발급하므로 {@link
 * JwtAuthenticationFilter} 이후의 인증 파이프라인은 이메일 로그인과 완전히 동일하게 동작한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2SuccessHandler oAuth2SuccessHandler,
            OAuth2FailureHandler oAuth2FailureHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        exception ->
                                exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/api/health",
                                                "/api/auth/signup",
                                                "/api/auth/login",
                                                "/oauth2/**",
                                                "/login/oauth2/**",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2Login(
                        oauth2 ->
                                oauth2.userInfoEndpoint(
                                                userInfo ->
                                                        userInfo.userService(
                                                                customOAuth2UserService))
                                        .successHandler(oAuth2SuccessHandler)
                                        .failureHandler(oAuth2FailureHandler))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
