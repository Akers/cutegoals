package com.cutegoals.family.controller;

import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.service.ChildProfileService;
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
 * REST controller for child profile management (Task 2.10).
 */
@RestController
@RequestMapping("/api/family/children")
@RequiredArgsConstructor
public class ChildProfileController {

    private static final Logger log = LoggerFactory.getLogger(ChildProfileController.class);

    private final ChildProfileService childProfileService;

    /**
     * POST /api/family/children - Create a child profile.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createChild(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);
        Long familyId = getFamilyId(request);

        String nickname = (String) body.get("nickname");
        String avatar = (String) body.get("avatar");
        String pin = (String) body.get("pin");

        Map<String, Object> result = childProfileService.createChildProfile(
                familyId, accountId, nickname, avatar, pin);
        return ResponseEntity.ok(ApiResponse.success(result, requestId));
    }

    /**
     * PUT /api/family/children/{id} - Update a child profile.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateChild(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);
        Long familyId = getFamilyId(request);

        Map<String, Object> result = childProfileService.updateChildProfile(
                id, familyId, accountId, body);
        return ResponseEntity.ok(ApiResponse.success(result, requestId));
    }

    /**
     * DELETE /api/family/children/{id} - Delete (anonymize) a child profile.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteChild(
            @PathVariable Long id,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);
        Long familyId = getFamilyId(request);

        childProfileService.deleteChildProfile(id, familyId, accountId);
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
