package com.cutegoals.instancemanagement.controller;

import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.instancemanagement.service.InstanceConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * System configuration management (Task 6.2).
 * GET/PUT /api/admin/config
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class InstanceConfigController {

    private final InstanceConfigService configService;

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getConfig() {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("requestId", requestId);
        }
        List<Map<String, Object>> config = configService.getAllConfig();
        return ResponseEntity.ok(ApiResponse.success(config, requestId));
    }

    @PutMapping("/config")
    public ResponseEntity<ApiResponse<Void>> updateConfig(
            @RequestBody Map<String, Object> updates,
            HttpServletRequest request) {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("requestId", requestId);
        }
        Long adminAccountId = (Long) request.getAttribute(AuthConstants.ATTR_ACCOUNT_ID);
        configService.updateConfig(updates, adminAccountId);
        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }
}
