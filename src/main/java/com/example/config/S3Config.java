package com.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@code file.storage.type=s3}일 때만 S3Client/S3Presigner를 등록한다. 로컬 프로파일(기본값 local)에서는 이 설정 자체가 평가되지
 * 않으므로 AWS_REGION 등 관련 환경변수가 없어도 기동에 영향이 없다.
 *
 * <p>{@link S3Presigner}는 {@link com.example.service.storage.S3FileStorageService#getRedirectUrl}이
 * 만드는 presigned GET URL에 쓰인다 — {@link com.example.controller.FileController#download}가 이미 {@code
 * StorageType.S3}일 때 302 리다이렉트를 전제로 작성돼 있어(서버가 파일 바이트를 거치지 않는 구조), 그 계약을 그대로 구현한다.
 */
@Configuration
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3Config {

    @Value("${file.storage.s3.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        // 자격증명은 로컬에서는 AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY 환경변수,
        // EC2 배포 시에는 IAM Role에서 SDK가 자동으로 찾는다 — 코드에 분기를 두지 않는다(CLAUDE.md 8장).
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
