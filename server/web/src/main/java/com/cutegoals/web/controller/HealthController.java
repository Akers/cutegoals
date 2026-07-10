package com.cutegoals.web.controller;

import com.cutegoals.auth.service.InitializationTokenService;
import com.cutegoals.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health endpoint for instance status checks.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final InitializationTokenService tokenService;

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        boolean initialized = tokenService.isInitialized();
        Map<String, Object> data = Map.of(
                "status", "UP",
                "initialized", initialized
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/api/instance/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        boolean initialized = tokenService.isInitialized();
        Map<String, Object> data = Map.of(
                "instanceStatus", initialized ? "INITIALIZED" : "UNINITIALIZED"
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
