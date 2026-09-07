package com.example.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ErrorCode;
import com.example.entity.StorageType;

/**
 * 로컬 디스크 저장소 구현 (M7 기본값).
 *
 * <p>{@code file.storage.type}이 없거나 {@code local}일 때 활성화된다({@code matchIfMissing = true}) — S3 구현이
 * 붙기 전까지는 설정을 비워 두어도 앱이 뜬다.
 */
@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path baseDir;

    /**
     * base-dir는 상대경로({@code ../upload})가 기본이라 작업 디렉터리에 따라 가리키는 곳이 달라진다. 여기서 절대경로로 확정해 두고 기동 로그로 남기는
     * 이유는, IDE로 실행할 때와 {@code mvnw spring-boot:run}으로 실행할 때 서로 다른 폴더에 파일이 쌓이는 상황을 눈으로 바로 확인하기 위함이다.
     */
    public LocalFileStorageService(@Value("${file.storage.local.base-dir}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void prepareBaseDir() {
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 기준 디렉터리를 만들지 못했습니다: " + baseDir, e);
        }
        log.info("로컬 파일 저장소 기준 경로: {}", baseDir);
    }

    @Override
    public StoredFile store(MultipartFile file, String storageKey) {
        Path target = resolveSafely(storageKey);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 저장하지 못했습니다: " + storageKey, e);
        }
        return new StoredFile(StorageType.LOCAL, storageKey, file.getSize());
    }

    @Override
    public Resource load(String storageKey) {
        Path target = resolveSafely(storageKey);
        if (!Files.isReadable(target)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return new PathResource(target);
    }

    /** 로컬 디스크는 애플리케이션이 직접 스트리밍하므로 리다이렉트할 주소가 없다. */
    @Override
    public String getRedirectUrl(String storageKey) {
        return null;
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveSafely(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 삭제하지 못했습니다: " + storageKey, e);
        }
    }

    @Override
    public StorageType getType() {
        return StorageType.LOCAL;
    }

    /**
     * 최종 경로가 base-dir 하위인지 검증한다. {@code store}/{@code load}/{@code delete}가 모두 이 메서드를 거치므로 경로 탈출
     * 방어가 한 곳에만 존재한다.
     *
     * <p>키는 서버가 UUID로 만들지만 검증을 저장 시점에만 두지 않는 이유는, 방어 대상이 업로드 요청이 아니라 <b>DB에 이미 들어 있는 {@code
     * storage_key}</b>이기 때문이다. 어떤 경로로든 그 값이 오염되면 {@code ../../etc/passwd} 같은 키로 base-dir 밖을 읽거나 지울
     * 수 있게 된다.
     *
     * <p>탈출을 {@code FILE_NOT_FOUND}로 돌려보내는 것은 {@code TODO_NOT_FOUND}와 같은 이유다 — 조작된 키와 없는 파일이 응답에서
     * 구별되면 그 자체가 탐색 신호가 된다.
     */
    private Path resolveSafely(String storageKey) {
        Path resolved = baseDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            log.warn("기준 경로를 벗어나는 storage_key 접근을 차단했습니다: {}", storageKey);
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return resolved;
    }
}
