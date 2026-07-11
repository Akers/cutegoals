package com.cutegoals.instancemanagement.controller;

import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.instancemanagement.service.AccountManagementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Account list and enable/disable management (Task 6.3).
 * GET /api/admin/accounts
 * POST /api/admin/accounts/{id}/enable
 * POST /api/admin/accounts/{id}/disable
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AccountManagementController {

    private final AccountManagementService accountManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccounts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("requestId", requestId);
        }
        Map<String, Object> data = accountManagementService.getAccounts(page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<Void>> enableAccount(
            @PathVariable Long id,
            HttpServletRequest request) {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("requestId", requestId);
        }
        Long adminAccountId = (Long) request.getAttribute(AuthConstants.ATTR_ACCOUNT_ID);
        accountManagementService.enableAccount(id, adminAccountId);
        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<Void>> disableAccount(
            @PathVariable Long id,
            HttpServletRequest request) {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("requestId", requestId);
        }
        Long adminAccountId = (Long) request.getAttribute(AuthConstants.ATTR_ACCOUNT_ID);
        accountManagementService.disableAccount(id, adminAccountId);
        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }
}
