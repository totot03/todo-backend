package com.example.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.entity.User;
import com.example.repository.UserRepository;

/**
 * httpOnly 쿠키({@code access_token})에서 JWT를 꺼내 SecurityContext에 인증 정보를 심는 필터.
 *
 * <p>쿠키가 없거나, 토큰이 만료/변조되었거나, 토큰은 유효하지만 사용자를 찾지 못해도 예외를 던지지 않고 그대로 다음 필터로 넘긴다. 인증 실패 여부의 최종 판정(401
 * 응답)은 이 필터의 몫이 아니라 SecurityConfig의 authorizeHttpRequests와 AuthenticationEntryPoint가 담당한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "access_token";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        resolveToken(request.getCookies())
                .filter(jwtTokenProvider::validateToken)
                .map(jwtTokenProvider::getUserId)
                .flatMap(userRepository::findById)
                .ifPresent(this::authenticate);

        filterChain.doFilter(request, response);
    }

    private Optional<String> resolveToken(Cookie[] cookies) {
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private void authenticate(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
