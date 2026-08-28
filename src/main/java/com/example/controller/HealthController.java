package com.example.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.common.response.ApiResponse;

/** M0 스캐폴딩 검증용 헬스체크 엔드포인트. API_SPEC.md 4.1 — 인증 불필요. */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("status", "UP"));
    }
}
