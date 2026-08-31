package com.example.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SecurityConfig의 인증·CORS 정책을 실제 필터 체인을 통해 검증한다.
 *
 * <p>{@code /api/auth/me}는 아직 컨트롤러가 없지만, Security 필터 체인은 DispatcherServlet의 핸들러 매핑보다 먼저 동작하므로 인증되지
 * 않은 요청은 "핸들러 없음(404)"이 아니라 "인증 필요(401)"로 먼저 걸러진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void healthEndpointIsAccessibleWithoutCookie() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void protectedPathWithoutCookieReturns401UnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void corsPreflightFromDisallowedOriginIsRejected() throws Exception {
        mockMvc.perform(
                        options("/api/health")
                                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
