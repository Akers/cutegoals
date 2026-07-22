package com.cutegoals.points.controller;

import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.points.PointsBalance;
import com.cutegoals.common.entity.points.PointsLedger;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.points.service.PointsService;
import com.cutegoals.task.mapper.TaskChildMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for points operations (Phase 4, Tasks 4.8-4.10).
 */
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointsController {

    private static final Logger log = LoggerFactory.getLogger(PointsController.class);

    private final PointsService pointsService;
    private final TaskChildMapper taskChildMapper;

    // ========== GET /api/points/balance/{childId} — Balance (Task 4.8) ==========

    @GetMapping("/balance/{childId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBalance(
            @PathVariable Long childId,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long familyId = pointsService.getSingleFamilyId();

        Long viewerChildId = null;
        if (roles.contains(AuthConstants.ROLE_CHILD)) {
            // Child role: childId MUST come from session identity, not URL
            Long sessionChildId = resolveChildIdFromSession(httpRequest);
            if (!sessionChildId.equals(childId)) {
                throw new BusinessException(ErrorCode.POINTS_FORBIDDEN, "Child identity mismatch");
            }
            viewerChildId = sessionChildId;
        } else {
            pointsService.verifyParentRole(roles);
        }

        PointsBalance balance = pointsService.getBalance(childId, familyId, viewerChildId);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("childId", balance.getChildId());
        data.put("balance", balance.getBalance());
        data.put("totalEarned", balance.getTotalEarned());
        data.put("version", balance.getVersion());

        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== POST /api/points/adjustments — Parent Adjustment (Task 4.9) ==========

    @PostMapping("/adjustments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adjustPoints(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);
        Long familyId = pointsService.getSingleFamilyId();

        Long childId = request.get("childId") != null ? ((Number) request.get("childId")).longValue() : null;
        if (childId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "childId is required");
        }

        PointsLedger ledger = pointsService.adjustPoints(request, childId, familyId, accountId, roles);

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", ledger.getId());
        data.put("childId", ledger.getChildId());
        data.put("type", ledger.getType());
        data.put("amount", ledger.getAmount());
        data.put("balanceAfter", ledger.getBalanceAfter());
        data.put("businessRef", ledger.getBusinessRef());
        data.put("reason", ledger.getReason());
        data.put("createdAt", ledger.getCreatedAt());

        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/points/ledger/{childId} — Ledger Query (Task 4.10) ==========

    @GetMapping("/ledger/{childId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryLedger(
            @PathVariable Long childId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long familyId = pointsService.getSingleFamilyId();

        Long viewerChildId = null;
        if (roles.contains(AuthConstants.ROLE_CHILD)) {
            // Child role: childId MUST come from session identity, not URL
            Long sessionChildId = resolveChildIdFromSession(httpRequest);
            if (!sessionChildId.equals(childId)) {
                throw new BusinessException(ErrorCode.POINTS_FORBIDDEN, "Child identity mismatch");
            }
            viewerChildId = sessionChildId;
        } else {
            pointsService.verifyParentRole(roles);
        }

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (page != null) params.put("page", page);
        if (pageSize != null) params.put("pageSize", pageSize);
        if (type != null) params.put("type", type);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);

        Map<String, Object> data = pointsService.queryLedger(childId, params, familyId, viewerChildId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/points/summary — Family Summary (Task 4.10) ==========

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFamilySummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        pointsService.verifyParentRole(roles);

        Long familyId = pointsService.getSingleFamilyId();

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);

        Map<String, Object> data = pointsService.getFamilySummary(params, familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== Helpers ==========

    private Long resolveChildIdFromSession(HttpServletRequest httpRequest) {
        // Child session: childId IS the profile ID, validate directly
        Long childId = (Long) httpRequest.getAttribute(AuthConstants.ATTR_CHILD_ID);
        if (childId != null) {
            ChildProfile profile = taskChildMapper.findActiveById(childId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_FORBIDDEN, "Child profile not found or inactive"));
            return childId;
        }
        // Parent session: resolve via family_member
        Long accountId = getAccountId(httpRequest);
        return taskChildMapper.findByAccountId(accountId)
                .map(ChildProfile::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_FORBIDDEN, "No child profile found for session"));
    }

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

    @SuppressWarnings("unchecked")
    private List<String> getRoles(HttpServletRequest request) {
        List<String> roles = (List<String>) request.getAttribute(AuthConstants.ATTR_ROLES);
        if (roles == null) {
            roles = List.of();
        }
        return roles;
    }
}
