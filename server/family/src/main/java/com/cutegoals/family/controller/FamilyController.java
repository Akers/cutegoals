package com.cutegoals.family.controller;

import com.cutegoals.auth.mapper.FamilyMemberMapper;
import com.cutegoals.auth.service.SessionService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.service.FamilyService;
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
 * REST controller for family management (Tasks 2.8, 2.11).
 */
@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private static final Logger log = LoggerFactory.getLogger(FamilyController.class);

    private final FamilyService familyService;
    private final FamilyMemberMapper familyMemberMapper;
    private final SessionService sessionService;

    /**
     * GET /api/family - Get current family information.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFamily(HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);
        Long familyId = getFamilyId(request);

        Map<String, Object> data = familyService.getFamily(familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    /**
     * PUT /api/family - Update family information (whitelist fields only).
     */
    @PutMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateFamily(
            @RequestBody Map<String, Object> updates,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);
        Long familyId = getFamilyId(request);

        Map<String, Object> data = familyService.updateFamily(familyId, updates, accountId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    /**
     * DELETE /api/family/members/{id} - Remove a member (parent protection).
     */
    @DeleteMapping("/members/{id}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long id,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);
        Long familyId = getFamilyId(request);

        var member = familyMemberMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!member.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (!AuthConstants.ROLE_PARENT.equals(member.getRole())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Can only remove PARENT members");
        }

        // Check last active parent protection
        long activeParentCount = familyMemberMapper.countActiveByFamilyIdAndRole(
                familyId, AuthConstants.ROLE_PARENT);
        if (activeParentCount <= 1) {
            throw new BusinessException(ErrorCode.LAST_ACTIVE_PARENT);
        }

        // Deactivate member
        familyMemberMapper.updateStatus(id, AuthConstants.MEMBER_INACTIVE);

        // Revoke all sessions of removed member
        sessionService.revokeAllSessions(member.getAccountId());

        log.info("Member removed: memberId={}, accountId={}, by accountId={}", id, member.getAccountId(), accountId);
        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }

    /**
     * POST /api/family/members/me/leave - Current parent leaves the family.
     */
    @PostMapping("/members/me/leave")
    public ResponseEntity<ApiResponse<Void>> leaveFamily(HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);
        Long familyId = getFamilyId(request);

        var member = familyMemberMapper.findByAccountIdAndRole(accountId, AuthConstants.ROLE_PARENT)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!member.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // Check last active parent protection
        long activeParentCount = familyMemberMapper.countActiveByFamilyIdAndRole(
                familyId, AuthConstants.ROLE_PARENT);
        if (activeParentCount <= 1) {
            throw new BusinessException(ErrorCode.LAST_ACTIVE_PARENT);
        }

        // Deactivate member
        familyMemberMapper.updateStatus(member.getId(), AuthConstants.MEMBER_INACTIVE);

        // Revoke all sessions of leaving member
        sessionService.revokeAllSessions(accountId);

        log.info("Member left family: memberId={}, accountId={}", member.getId(), accountId);
        return ResponseEntity.ok(ApiResponse.success(null, requestId));
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
