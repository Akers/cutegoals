package com.cutegoals.web.controller;

import com.cutegoals.auth.service.InitializationTokenService;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.instancemanagement.service.InstanceHealthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Health endpoint for instance status checks (Tasks 2.1, 6.1, 8.6).
 * <ul>
 *   <li>GET /api/health - unauthenticated liveness probe</li>
 *   <li>GET /api/instance/status - unauthenticated initialization status</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final InitializationTokenService tokenService;
    private final InstanceHealthService instanceHealthService;

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        Map<String, Object> data = instanceHealthService.getPublicHealth();
        String status = (String) data.get("status");

        if ("DOWN".equals(status)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(data, requestId));
        }
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
