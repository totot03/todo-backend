package com.example.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.common.response.ApiResponse;
import com.example.dto.auth.LoginRequest;
import com.example.dto.auth.SignupRequest;
import com.example.dto.auth.UserResponse;
import com.example.security.AccessTokenCookieWriter;
import com.example.security.CustomUserDetails;
import com.example.service.AuthService;
import com.example.service.AuthService.AuthResult;

/**
 * 인증 4개 엔드포인트. API_SPEC.md 2.1~2.4.
 *
 * <p>토큰은 오직 {@code Set-Cookie}(httpOnly)로만 내려주고 응답 본문에는 {@link UserResponse}만 담는다 — {@code
 * Authorization} 헤더나 응답 바디에 토큰 값을 노출하지 않는다(CLAUDE.md 불변 규칙). 쿠키 배선 자체는 {@link
 * AccessTokenCookieWriter}에 위임한다 — 구글 OAuth2 로그인(M2-B) 성공 핸들러도 같은 컴포넌트를 써서 쿠키 스펙이 두 경로에서 갈라지지 않게
 * 한다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccessTokenCookieWriter accessTokenCookieWriter;

    public AuthController(
            AuthService authService, AccessTokenCookieWriter accessTokenCookieWriter) {
        this.authService = authService;
        this.accessTokenCookieWriter = accessTokenCookieWriter;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signup(
            @Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        AuthResult result = authService.signup(request);
        accessTokenCookieWriter.attach(response, result.token());
        return ApiResponse.success(result.user());
    }

    @PostMapping("/login")
    public ApiResponse<UserResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResult result = authService.login(request);
        accessTokenCookieWriter.attach(response, result.token());
        return ApiResponse.success(result.user());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        accessTokenCookieWriter.expire(response);
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(UserResponse.from(userDetails.getUser()));
    }
}
