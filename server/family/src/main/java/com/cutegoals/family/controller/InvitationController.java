package com.cutegoals.family.controller;

import com.cutegoals.auth.mapper.AccountMapper;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.service.InvitationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for parent invitation management (Task 2.9).
 */
@RestController
@RequestMapping("/api/family/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private static final Logger log = LoggerFactory.getLogger(InvitationController.class);

    private final InvitationService invitationService;
    private final AccountMapper accountMapper;

    /**
     * POST /api/family/invitations - Create a parent invitation.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createInvitation(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);
        Long familyId = getFamilyId(request);

        String targetPhone = (String) body.get("targetPhone");
        if (targetPhone == null || targetPhone.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "targetPhone is required");
        }

        String idempotencyKey = (String) body.get("idempotencyKey");

        Map<String, Object> result = invitationService.createInvitation(
                familyId, accountId, targetPhone, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(result, requestId));
    }

    /**
     * POST /api/family/invitations/{id}/accept - Accept a pending invitation.
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<Map<String, Object>>> acceptInvitation(
            @PathVariable Long id,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);

        // Get account phone
        var account = accountMapper.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Map<String, Object> result = invitationService.acceptInvitation(id, accountId, account.getPhone());
        return ResponseEntity.ok(ApiResponse.success(result, requestId));
    }

    /**
     * POST /api/family/invitations/{id}/reject - Reject a pending invitation.
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rejectInvitation(
            @PathVariable Long id,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);

        var account = accountMapper.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Map<String, Object> result = invitationService.rejectInvitation(id, accountId, account.getPhone());
        return ResponseEntity.ok(ApiResponse.success(result, requestId));
    }

    /**
     * POST /api/family/invitations/{id}/revoke - Revoke a pending invitation.
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeInvitation(
            @PathVariable Long id,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);

        Map<String, Object> result = invitationService.revokeInvitation(id, accountId);
        return ResponseEntity.ok(ApiResponse.success(result, requestId));
    }

    // === Helpers ===

    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private Long getAccountId(HttpServletRequest request) {
        Long id = (Long) request.getAttribute(AuthConstants.ATTR_ACCOUNT_ID);
        if (id == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    private Long getFamilyId(HttpServletRequest request) {
        Long id = (Long) request.getAttribute(AuthConstants.ATTR_FAMILY_ID);
        return id;
    }
}
