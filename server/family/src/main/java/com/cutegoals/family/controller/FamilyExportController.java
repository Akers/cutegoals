package com.cutegoals.family.controller;

import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.service.FamilyExportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Family data export endpoint (Task 6.7).
 * GET /api/family/export
 */
@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyExportController {

    private final FamilyExportService familyExportService;
    private final AuditService auditService;

    @GetMapping("/export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportFamily(
            HttpServletRequest request) {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("requestId", requestId);
        }

        Long accountId = (Long) request.getAttribute(AuthConstants.ATTR_ACCOUNT_ID);
        @SuppressWarnings("unchecked")
        java.util.List<String> roles = (java.util.List<String>) request.getAttribute(AuthConstants.ATTR_ROLES);

        // Only PARENT role can export
        if (roles == null || !roles.contains(AuthConstants.ROLE_PARENT)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only PARENT can export family data");
        }

        Long familyId = (Long) request.getAttribute(AuthConstants.ATTR_FAMILY_ID);
        if (familyId == null) {
            // Look up the family from the account
            // In the MVP, the family is derived from the account's membership
        }

        Map<String, Object> data = familyExportService.exportFamilyData(accountId);
        auditService.record(AuditEvent.FAMILY_EXPORTED, accountId, "SUCCESS",
                "Family data exported: accountId=" + accountId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }
}
