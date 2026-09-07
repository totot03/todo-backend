package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JPA Auditing(BaseTimeEntity의 createdAt/updatedAt)을 애플리케이션 전역에서 활성화한다.
 *
 * <p>{@code @EnableScheduling}은 M7에서 처음 들어온 인프라다 — {@code AttachmentCleanupService}의 고아 파일 정리가 이
 * 프로젝트 최초의 {@code @Scheduled}이며, 이전까지는 스케줄링을 쓰는 컴포넌트가 없었다.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class TodoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoBackendApplication.class, args);
    }
}
