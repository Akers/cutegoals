package com.cutegoals.web.controller;

import com.cutegoals.auth.service.InitializationTokenService;
import com.cutegoals.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Health endpoint for instance status checks (Tasks 2.1, 6.1).
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final InitializationTokenService tokenService;

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        boolean initialized = tokenService.isInitialized();
        Map<String, Object> data = Map.of(
                "status", "UP",
                "initialized", initialized
        );
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    @GetMapping("/api/instance/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        boolean initialized = tokenService.isInitialized();
        Map<String, Object> data = Map.of(
                "instanceStatus", initialized ? "INITIALIZED" : "UNINITIALIZED"
        );
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }
}
