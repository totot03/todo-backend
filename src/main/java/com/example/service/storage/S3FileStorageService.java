package com.example.service.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.common.exception.BusinessException;
import com.example.common.exception.ErrorCode;
import com.example.entity.StorageType;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3 저장소 구현 (운영). {@code file.storage.type=s3}일 때만 활성화되고, 로컬({@link LocalFileStorageService})과 같은
 * 시점에 동시에 뜨지 않는다({@code havingValue} 분기가 서로 배타적).
 *
 * <p>{@link #getRedirectUrl}은 {@link com.example.controller.FileController}가 이미 전제하고 있는 계약대로
 * presigned GET URL을 만들어 돌려준다 — 파일 바이트가 이 서버를 거치지 않고 클라이언트가 S3에서 직접 받는다. {@link #load}는 이 구현체에서는
 * 쓰이지 않지만(다운로드 경로가 getRedirectUrl로만 빠짐), 인터페이스 계약을 지키기 위해 직접 스트리밍으로 구현해 둔다.
 */
@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3FileStorageService implements FileStorageService {

    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(10);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3FileStorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${file.storage.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    @Override
    public StoredFile store(MultipartFile file, String storageKey) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageKey)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 S3에 저장하지 못했습니다: " + storageKey, e);
        } catch (S3Exception e) {
            throw new UncheckedIOException(new IOException(e));
        }
        return new StoredFile(StorageType.S3, storageKey, file.getSize());
    }

    @Override
    public Resource load(String storageKey) {
        try {
            ResponseInputStream<GetObjectResponse> object =
                    s3Client.getObject(
                            GetObjectRequest.builder().bucket(bucket).key(storageKey).build());
            long contentLength = object.response().contentLength();
            return new InputStreamResource(object) {
                @Override
                public long contentLength() {
                    return contentLength;
                }

                @Override
                public String getFilename() {
                    return storageKey;
                }
            };
        } catch (S3Exception e) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    /**
     * presigned GET URL을 만든다. {@link com.example.controller.FileController#download}가 302로 리다이렉트할
     * 주소다.
     */
    @Override
    public String getRedirectUrl(String storageKey) {
        GetObjectRequest getObjectRequest =
                GetObjectRequest.builder().bucket(bucket).key(storageKey).build();
        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(PRESIGN_DURATION)
                        .getObjectRequest(getObjectRequest)
                        .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
        } catch (S3Exception e) {
            // 이미 지워졌거나 없는 키는 조용히 넘어간다 — 고아 정리 스케줄러가 재시도로 반복 호출해도 안전해야 한다.
        }
    }

    @Override
    public StorageType getType() {
        return StorageType.S3;
    }
}
