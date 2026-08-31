package com.example.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * signup → login → me → logout → (쿠키 없이) me 401 순서로, ROADMAP.md M2-A DoD의 curl 시나리오와 동일한 흐름을
 * MockMvc로 재현해 API_SPEC.md 2.1~2.4 응답 형식을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String COOKIE_NAME = "access_token";

    @Autowired private MockMvc mockMvc;

    @Test
    void signupLoginMeLogoutFlowMatchesApiSpec() throws Exception {
        // 반복 실행 시 EMAIL_DUPLICATED와 충돌하지 않도록 매번 다른 이메일을 사용한다.
        String email = "test-" + System.currentTimeMillis() + "@example.com";

        MvcResult signupResult =
                mockMvc.perform(
                                post("/api/auth/signup")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email":"%s","password":"abcdef","nickname":"홍길동"}
                                                """
                                                        .formatted(email)))
                        .andExpect(status().isCreated())
                        .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.email").value(email))
                        .andExpect(jsonPath("$.data.nickname").value("홍길동"))
                        .andExpect(jsonPath("$.data.provider").value("LOCAL"))
                        .andReturn();
        assertNotNull(extractAccessToken(signupResult));

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email":"%s","password":"abcdef"}
                                                """
                                                        .formatted(email)))
                        .andExpect(status().isOk())
                        .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                        .andReturn();
        String loginToken = extractAccessToken(loginResult);

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie(COOKIE_NAME, loginToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE_NAME, loginToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 로그아웃 이후에는 (실제 브라우저라면 쿠키가 지워졌겠지만) 쿠키 없이 호출한 것과 동일하게 401이어야 한다.
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private String extractAccessToken(MvcResult result) {
        String setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.startsWith(COOKIE_NAME + "="));
        return setCookieHeader.split(";", 2)[0].substring((COOKIE_NAME + "=").length());
    }
}
