package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA Auditing(BaseTimeEntity의 createdAt/updatedAt)을 애플리케이션 전역에서 활성화한다. */
@SpringBootApplication
@EnableJpaAuditing
public class TodoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoBackendApplication.class, args);
    }
}
