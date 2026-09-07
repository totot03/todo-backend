package com.example.service.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ErrorCode;
import com.example.entity.StorageType;

/** 로컬 저장소가 기준 경로 밖을 건드리지 못하는지 검증한다 (NFR-S09). */
class LocalFileStorageServiceTest {

    /** 기준 경로를 벗어나려는 키. store/load/delete 어느 쪽으로 들어와도 같은 결과여야 한다. */
    private static final String ESCAPING_KEY = "../../etc/passwd";

    @TempDir Path tempDir;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(tempDir.toString());
        storageService.prepareBaseDir();
    }

    @Test
    void storesFileUnderBaseDirAndCreatesMissingParents() throws IOException {
        MockMultipartFile file =
                new MockMultipartFile("file", "원본.png", "image/png", "이미지-바이트".getBytes());

        StoredFile stored = storageService.store(file, "todos/1/2026/09/abc.png");

        Path written = tempDir.resolve("todos/1/2026/09/abc.png");
        assertTrue(Files.exists(written));
        assertEquals("이미지-바이트", Files.readString(written));
        assertEquals(StorageType.LOCAL, stored.storageType());
        assertEquals("todos/1/2026/09/abc.png", stored.storageKey());
        assertEquals(file.getSize(), stored.size());
    }

    @Test
    void rejectsEscapingKeyOnStore() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[] {1, 2, 3});

        BusinessException e =
                assertThrows(
                        BusinessException.class, () -> storageService.store(file, ESCAPING_KEY));

        assertEquals(ErrorCode.FILE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void rejectsEscapingKeyOnLoad() {
        BusinessException e =
                assertThrows(BusinessException.class, () -> storageService.load(ESCAPING_KEY));

        assertEquals(ErrorCode.FILE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void rejectsEscapingKeyOnDelete() {
        BusinessException e =
                assertThrows(BusinessException.class, () -> storageService.delete(ESCAPING_KEY));

        assertEquals(ErrorCode.FILE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void loadingMissingFileIsIndistinguishableFromEscapingKey() {
        BusinessException e =
                assertThrows(
                        BusinessException.class,
                        () -> storageService.load("todos/1/2026/09/없는파일.png"));

        assertEquals(ErrorCode.FILE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void deleteIsIdempotentForMissingFile() {
        storageService.delete("todos/1/2026/09/없는파일.png");
    }

    @Test
    void localStorageHasNoRedirectUrl() {
        assertNull(storageService.getRedirectUrl("todos/1/2026/09/abc.png"));
        assertEquals(StorageType.LOCAL, storageService.getType());
    }
}
