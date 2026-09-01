package com.example.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 할 일 CRUD 6개 엔드포인트를 ROADMAP.md M3 DoD 시나리오(생성→목록 페이지네이션→수정→토글→삭제)대로 MockMvc로 재현해 API_SPEC.md 3장 응답
 * 형식과 CLAUDE.md 불변 규칙(소유권 404, sanitize, soft delete)을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TodoControllerTest {

    private static final String COOKIE_NAME = "access_token";

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void todoCrudFlowMatchesApiSpec() throws Exception {
        String ownerToken = signup("owner");
        String otherToken = signup("other");
        Cookie ownerCookie = new Cookie(COOKIE_NAME, ownerToken);
        Cookie otherCookie = new Cookie(COOKIE_NAME, otherToken);

        // 1) 생성 — description의 <script>는 sanitize되어 제거되어야 한다.
        MvcResult createResult =
                mockMvc.perform(
                                post("/api/todos")
                                        .cookie(ownerCookie)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"title":"주간 회의 자료 준비",
                                                 "description":"<p>본문</p><script>alert(1)</script>",
                                                 "dueDate":"2026-09-01","priority":"HIGH"}
                                                """))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.title").value("주간 회의 자료 준비"))
                        .andExpect(jsonPath("$.data.description").value("<p>본문</p>"))
                        .andExpect(jsonPath("$.data.priority").value("HIGH"))
                        .andExpect(jsonPath("$.data.completed").value(false))
                        .andReturn();
        Long id = extractId(createResult);

        // priority 생략 시 기본값 MEDIUM.
        mockMvc.perform(
                        post("/api/todos")
                                .cookie(ownerCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"title":"기본 우선순위 확인용"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.priority").value("MEDIUM"));

        // 2) 목록 — 페이지네이션 필드 확인.
        mockMvc.perform(
                        get("/api/todos")
                                .cookie(ownerCookie)
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.first").value(true));

        // completed 필터 — 아직 미완료이므로 completed=false에는 포함, completed=true에는 없어야 한다.
        mockMvc.perform(get("/api/todos").cookie(ownerCookie).param("completed", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(id)).exists());
        mockMvc.perform(get("/api/todos").cookie(ownerCookie).param("completed", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(id)).doesNotExist());

        // 키워드 검색 — 제목 부분일치.
        mockMvc.perform(get("/api/todos").cookie(ownerCookie).param("keyword", "회의"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(id)).exists());
        mockMvc.perform(get("/api/todos").cookie(ownerCookie).param("keyword", "존재하지않는키워드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // 3) 상세 조회.
        mockMvc.perform(get("/api/todos/" + id).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

        // 4) 부분 수정 — title만 보내면 나머지는 유지되어야 한다.
        mockMvc.perform(
                        patch("/api/todos/" + id)
                                .cookie(ownerCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"title":"수정된 제목"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-09-01"));

        // 5) 토글 — 반전 후 다시 토글하면 원복.
        mockMvc.perform(patch("/api/todos/" + id + "/toggle").cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
        mockMvc.perform(patch("/api/todos/" + id + "/toggle").cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(false));

        // 6) 타 사용자는 상세/수정/토글 전부 404 TODO_NOT_FOUND (403 아님 — 존재 여부 비노출).
        mockMvc.perform(get("/api/todos/" + id).cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
        mockMvc.perform(
                        patch("/api/todos/" + id)
                                .cookie(otherCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"title":"침입 시도"}
                                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
        mockMvc.perform(patch("/api/todos/" + id + "/toggle").cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
        mockMvc.perform(delete("/api/todos/" + id).cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));

        // 7) 삭제(Soft Delete) — 응답은 success:true, data:null.
        mockMvc.perform(delete("/api/todos/" + id).cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 8) 삭제 후에는 소유자 본인도 404, 목록에서도 제외된다.
        mockMvc.perform(get("/api/todos/" + id).cookie(ownerCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));
        mockMvc.perform(get("/api/todos").cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(id)).doesNotExist());

        // 9) 이미 삭제된 항목을 다시 삭제해도 404 (물리 삭제가 아니므로 idempotent 성공이 아니라 404가 맞다).
        mockMvc.perform(delete("/api/todos/" + id).cookie(ownerCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TODO_NOT_FOUND"));

        // 10) 쿠키 없이 접근하면 401 UNAUTHORIZED.
        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // 11) 검증 실패 — title 누락 시 400 VALIDATION_FAILED + fieldErrors.
        mockMvc.perform(
                        post("/api/todos")
                                .cookie(ownerCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"title":""}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors").isArray());

        // 12) 존재하지 않는 priority 값 — JSON 파싱 실패도 VALIDATION_FAILED(400)로 통일된다.
        mockMvc.perform(
                        post("/api/todos")
                                .cookie(ownerCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"title":"제목","priority":"URGENT"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    /** 반복 실행 시 EMAIL_DUPLICATED와 충돌하지 않도록 매번 다른 이메일로 회원가입하고 access_token 쿠키 값을 반환한다. */
    private String signup(String prefix) throws Exception {
        String email = "%s-%d@example.com".formatted(prefix, System.currentTimeMillis());
        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/signup")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email":"%s","password":"abcdef","nickname":"홍길동"}
                                                """
                                                        .formatted(email)))
                        .andExpect(status().isCreated())
                        .andReturn();
        String setCookieHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeader);
        return setCookieHeader.split(";", 2)[0].substring((COOKIE_NAME + "=").length());
    }

    private Long extractId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }
}
