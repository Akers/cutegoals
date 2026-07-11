package com.cutegoals.instancemanagement.controller;

import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.instancemanagement.service.InstanceHealthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Admin health check endpoint (Task 6.1).
 * GET /api/admin/health - admin detailed health status.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminHealthController {

    private final InstanceHealthService instanceHealthService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminHealth() {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("requestId", requestId);
        }
        Map<String, Object> data = instanceHealthService.getAdminHealth();
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }
}
