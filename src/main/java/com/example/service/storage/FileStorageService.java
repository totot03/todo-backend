package com.example.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.example.entity.StorageType;

/**
 * 파일 저장소 추상화 (M7). 구현은 저장 위치만 책임지고, 크기·형식 검증과 {@code storageKey} 생성 규칙은 상위 서비스가 정한다.
 *
 * <p>{@link #load}와 {@link #getRedirectUrl}이 함께 있는 이유는 두 저장소의 전달 방식이 다르기 때문이다. 로컬 디스크는 애플리케이션이 직접
 * 스트리밍하는 수밖에 없지만, S3는 presigned URL로 리다이렉트해 파일 바이트가 애플리케이션을 거치지 않게 하는 편이 낫다. 컨트롤러는 {@code
 * storage_type}을 보고 둘 중 하나를 고른다.
 *
 * <p>현재 구현체는 {@link LocalFileStorageService} 하나이며, S3 구현은 v1.1에서 추가한다.
 */
public interface FileStorageService {

    /** 파일을 {@code storageKey} 위치에 저장한다. 키는 호출자가 만들어 넘긴다. */
    StoredFile store(MultipartFile file, String storageKey);

    /** 스트리밍용 리소스를 연다. 로컬 저장소가 쓰는 경로다. */
    Resource load(String storageKey);

    /** presigned URL 등 리다이렉트할 주소. 직접 스트리밍하는 저장소는 {@code null}을 반환한다. */
    String getRedirectUrl(String storageKey);

    void delete(String storageKey);

    StorageType getType();
}
