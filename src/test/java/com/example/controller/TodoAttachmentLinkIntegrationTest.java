package com.example.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Paths;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import com.example.service.AttachmentCleanupService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Todo 본문의 이미지 링크가 sanitize·링크 동기화·고아 정리 가드와 어떻게 맞물리는지 MockMvc + 실제 DB로 검증한다 (FR-T14).
 *
 * <p>{@code AttachmentServiceTest.syncLinks*}가 리포지토리를 mock으로 두고 로직만 검증한 것과 달리, 이 클래스는 실제 Postgres
 * 위에서 JPQL {@code LIKE} 쿼리와 {@code @SQLRestriction}까지 통째로 거쳐 같은 결론이 나오는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TodoAttachmentLinkIntegrationTest {

    private static final String COOKIE_NAME = "access_token";
    private static final byte[] PNG_BYTES = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private AttachmentCleanupService attachmentCleanupService;
    @PersistenceContext private EntityManager entityManager;

    @Value("${file.storage.local.base-dir}")
    private String baseDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanUpUploadDir() throws java.io.IOException {
        FileSystemUtils.deleteRecursively(Paths.get(baseDir).toAbsolutePath().normalize());
    }

    @Test
    void externalUrlImageIsRemovedFromDescription() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, signup("external"));

        MvcResult result =
                createTodo(
                        cookie,
                        "외부 이미지 테스트",
                        "<p>본문</p><img src=\"https://evil.example.com/tracker.png\">");

        assertFalse(descriptionOf(result).contains("<img"));
    }

    @Test
    void javascriptUrlImageIsRemovedFromDescription() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, signup("javascript"));

        MvcResult result =
                createTodo(cookie, "javascript 이미지 테스트", "<img src=\"javascript:alert(1)\">");

        assertFalse(descriptionOf(result).contains("<img"));
        assertFalse(descriptionOf(result).contains("javascript:"));
    }

    @Test
    void validFileApiImageSurvivesSanitize() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, signup("valid"));
        String uuid = uploadImage(cookie, "사진.png");

        MvcResult result =
                createTodo(cookie, "정상 이미지 테스트", "<img src=\"/api/files/" + uuid + "\">");

        assertTrue(descriptionOf(result).contains("/api/files/" + uuid));
    }

    @Test
    void creatingTodoWithImageInBodyFillsAttachmentTodoId() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, signup("linkfill"));
        String uuid = uploadImage(cookie, "사진.png");

        MvcResult result = createTodo(cookie, "링크 채움 테스트", "<img src=\"/api/files/" + uuid + "\">");
        Long todoId = idOf(result);

        assertEquals(todoId, todoIdOfAttachment(uuid));
    }

    @Test
    void referencingAnotherUsersUuidDoesNotChangeItsTodoId() throws Exception {
        Cookie ownerCookie = new Cookie(COOKIE_NAME, signup("attacker-target"));
        String othersUuid = uploadImage(ownerCookie, "타인소유.png");

        Cookie attackerCookie = new Cookie(COOKIE_NAME, signup("attacker"));
        createTodo(attackerCookie, "침입 시도", "<img src=\"/api/files/" + othersUuid + "\">");

        // 소유자가 아닌 사람이 본문에 uuid를 적어 넣었다고 해서 그 첨부의 todo_id가 바뀌면 안 된다.
        assertNull(todoIdOfAttachment(othersUuid));
    }

    /**
     * {@code AttachmentCleanupServiceTest}가 mock으로 검증한 삭제 직전 가드를 실제 Postgres 위에서 재현한다. {@code
     * todo_id}가 null인 상태에서도 어떤 활성 Todo의 본문이 여전히 그 uuid를 참조 중이면(예: syncLinks가 도는 순간과 정리 스케줄러가 도는 순간
     * 사이의 경합) 삭제되면 안 된다.
     */
    @Test
    @Transactional
    void orphanCandidateStillReferencedByActiveTodoIsExcludedFromCleanup() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, signup("racecondition"));
        String uuid = uploadImage(cookie, "경합.png");
        createTodo(cookie, "경합 테스트", "<img src=\"/api/files/" + uuid + "\">");
        assertNotNull(todoIdOfAttachment(uuid)); // 정상적으로는 링크돼 있다.

        // todo_id만 강제로 null로 되돌리고(경합 재현) updated_at을 24시간 이전으로 늙혀 고아 정리 후보 조건을 만든다.
        // 본문은 그대로 두므로 countActiveReferencing은 여전히 이 uuid를 찾아내야 한다.
        entityManager
                .createNativeQuery(
                        "UPDATE attachments SET todo_id = NULL, updated_at = :threshold WHERE uuid = CAST(:uuid AS uuid)")
                .setParameter("threshold", LocalDateTime.now().minusHours(25))
                .setParameter("uuid", uuid)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        int deletedCount = attachmentCleanupService.cleanupOrphans();

        assertEquals(0, deletedCount);
        Object deletedAt =
                entityManager
                        .createNativeQuery(
                                "SELECT deleted_at FROM attachments WHERE uuid = CAST(:uuid AS uuid)")
                        .setParameter("uuid", uuid)
                        .getSingleResult();
        assertNull(deletedAt, "활성 Todo가 참조 중인 첨부는 고아 정리에서 제외되어야 한다");
    }

    private String uploadImage(Cookie cookie, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "image/png", PNG_BYTES);
        MvcResult result =
                mockMvc.perform(multipart("/api/files/images").file(file).cookie(cookie))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("uuid").asString();
    }

    private MvcResult createTodo(Cookie cookie, String title, String description) throws Exception {
        return mockMvc.perform(
                        post("/api/todos")
                                .cookie(cookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper
                                                .createObjectNode()
                                                .put("title", title)
                                                .put("description", description)
                                                .toString()))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String descriptionOf(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("description").asString();
    }

    private Long idOf(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private Long todoIdOfAttachment(String uuid) {
        Object todoId =
                entityManager
                        .createNativeQuery(
                                "SELECT todo_id FROM attachments WHERE uuid = CAST(:uuid AS uuid)")
                        .setParameter("uuid", uuid)
                        .getSingleResult();
        return todoId == null ? null : ((Number) todoId).longValue();
    }

    /** 반복 실행 시 EMAIL_DUPLICATED와 충돌하지 않도록 매번 다른 이메일로 회원가입하고 access_token 쿠키 값을 반환한다. */
    private String signup(String prefix) throws Exception {
        String email = "%s-%d@example.com".formatted(prefix, System.nanoTime());
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
}
