package com.example.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 이미지 업로드·조회 2개 엔드포인트를 MockMvc로 검증한다 (FR-T14, FEATURE_IMAGE_UPLOAD.md 3장).
 *
 * <p>{@code TodoControllerTest}와 같은 스타일을 따른다. 저장 경로는 {@code application-test.properties}의 {@code
 * ./target/test-upload}이며, 각 테스트 뒤에 정리한다 — {@code target/}은 gitignore 대상이지만 반복 실행 시 파일이 계속 쌓이는 것을
 * 막기 위함이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileControllerTest {

    private static final String COOKIE_NAME = "access_token";

    /** 최소한의 유효한 PNG 헤더. ImageTypeDetector는 선두 바이트만 읽으므로 완전한 이미지일 필요는 없다. */
    private static final byte[] PNG_BYTES = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    @Autowired private MockMvc mockMvc;
    @PersistenceContext private EntityManager entityManager;

    @Value("${file.storage.local.base-dir}")
    private String baseDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void cleanUpUploadDir() throws IOException {
        FileSystemUtils.deleteRecursively(Paths.get(baseDir).toAbsolutePath().normalize());
    }

    @Test
    void uploadingImageCreatesAttachmentAndWritesFileToDisk() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, signup("uploader"));

        MvcResult result =
                uploadPng(cookie, "스크린샷.png").andExpect(status().isCreated()).andReturn();
        String uuid = extractField(result, "uuid");

        // uuid 컬럼은 PostgreSQL 네이티브 uuid 타입이라 문자열 파라미터를 암묵적으로 캐스팅해 주지 않는다 — 명시적으로 캐스팅한다.
        String storageKey =
                (String)
                        entityManager
                                .createNativeQuery(
                                        "SELECT storage_key FROM attachments WHERE uuid = CAST(:uuid AS uuid)")
                                .setParameter("uuid", uuid)
                                .getSingleResult();
        Path written = Paths.get(baseDir).toAbsolutePath().normalize().resolve(storageKey);
        assertTrue(Files.exists(written), "업로드된 파일이 디스크에 실제로 생성되어야 한다: " + written);
    }

    @Test
    void rejectsFileLargerThan5MbWithFileTooLarge() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, signup("toolarge"));
        // 크기 검증이 매직 바이트 판정보다 먼저 실행되므로(AttachmentService.upload), 내용은 이미지가 아니어도 된다.
        MockMultipartFile huge =
                new MockMultipartFile(
                        "file", "큰파일.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

        mockMvc.perform(multipart("/api/files/images").file(huge).cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void rejectsFileDisguisedAsPngWithInvalidFileType() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, signup("disguised"));
        MockMultipartFile disguised =
                new MockMultipartFile("file", "위장.png", "image/png", "그냥 텍스트입니다".getBytes());

        mockMvc.perform(multipart("/api/files/images").file(disguised).cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
    }

    @Test
    void uploadingWithoutCookieReturns401Unauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "그림.png", "image/png", PNG_BYTES);

        mockMvc.perform(multipart("/api/files/images").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void downloadingOtherUsersFileReturns404FileNotFound() throws Exception {
        Cookie ownerCookie = new Cookie(COOKIE_NAME, signup("owner"));
        Cookie otherCookie = new Cookie(COOKIE_NAME, signup("other"));
        MvcResult uploaded =
                uploadPng(ownerCookie, "비밀.png").andExpect(status().isCreated()).andReturn();
        String uuid = extractField(uploaded, "uuid");

        mockMvc.perform(get("/api/files/" + uuid).cookie(otherCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void ownerCanDownloadUploadedImageWithSecurityHeaders() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, signup("downloader"));
        MvcResult uploaded =
                uploadPng(cookie, "다운로드.png").andExpect(status().isCreated()).andReturn();
        String uuid = extractField(uploaded, "uuid");

        MvcResult downloaded =
                mockMvc.perform(get("/api/files/" + uuid).cookie(cookie))
                        .andExpect(status().isOk())
                        .andReturn();

        // 응답 본문이 ApiResponse 봉투가 아니라 이미지 바이트 그대로임을 확인한다.
        assertArrayEquals(PNG_BYTES, downloaded.getResponse().getContentAsByteArray());
        assertEquals("image/png", downloaded.getResponse().getContentType());
        assertEquals("inline", downloaded.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("nosniff", downloaded.getResponse().getHeader("X-Content-Type-Options"));
        assertEquals(
                "default-src 'none'",
                downloaded.getResponse().getHeader("Content-Security-Policy"));
    }

    /**
     * 실제 위험 지점은 파일명이 아니라 DB의 {@code storage_key}다. 파일명은 서버가 UUID로 만들므로 "{@code ../}가 든 업로드 파일명"은
     * 애초에 성립하지 않는다 — 이 테스트는 그 지점을 직접 찌른다: 정상 업로드 후 DB의 storage_key만 네이티브 쿼리로 오염시켜, {@code
     * LocalFileStorageService.resolveSafely}가 base-dir 밖을 열지 못하게 막는지 확인한다.
     */
    @Test
    @Transactional
    void corruptedStorageKeyIsRejectedAsFileNotFoundNotPathTraversal() throws Exception {
        Cookie cookie = new Cookie(COOKIE_NAME, signup("corrupted"));
        MvcResult uploaded =
                uploadPng(cookie, "정상.png").andExpect(status().isCreated()).andReturn();
        String uuid = extractField(uploaded, "uuid");

        // DB 값을 직접 오염시키는 네이티브 UPDATE라 트랜잭션이 필요하다 — 이 테스트 메서드에만 @Transactional을 붙인다.
        entityManager
                .createNativeQuery(
                        "UPDATE attachments SET storage_key = :key WHERE uuid = CAST(:uuid AS uuid)")
                .setParameter("key", "../../../../etc/passwd")
                .setParameter("uuid", uuid)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/files/" + uuid).cookie(cookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions uploadPng(
            Cookie cookie, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "image/png", PNG_BYTES);
        return mockMvc.perform(multipart("/api/files/images").file(file).cookie(cookie));
    }

    private String extractField(MvcResult result, String field) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path(field).asString();
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
