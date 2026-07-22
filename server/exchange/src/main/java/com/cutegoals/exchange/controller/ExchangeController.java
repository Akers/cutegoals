package com.cutegoals.exchange.controller;

import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.entity.exchange.Exchange;
import com.cutegoals.common.entity.exchange.ExchangeSnapshot;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.exchange.service.ExchangeService;
import com.cutegoals.task.mapper.TaskChildMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for exchange operations (Phase 5, Tasks 5.7-5.11).
 */
@RestController
@RequestMapping("/api/exchanges")
@RequiredArgsConstructor
public class ExchangeController {

    private static final Logger log = LoggerFactory.getLogger(ExchangeController.class);

    private final ExchangeService exchangeService;
    private final FamilyMapper familyMapper;
    private final TaskChildMapper taskChildMapper;

    // ========== POST /api/exchanges/direct — Direct Exchange (Child, Task 5.7) ==========

    @PostMapping("/direct")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createDirectExchange(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        if (!roles.contains(AuthConstants.ROLE_CHILD)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only children can create exchanges");
        }

        // Verify idempotencyKey is present
        if (!request.containsKey("idempotencyKey") || request.get("idempotencyKey") == null
                || request.get("idempotencyKey").toString().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.EXCHANGE_IDEMPOTENCY_KEY_REQUIRED,
                    "idempotencyKey is required");
        }

        // Derive childId from authenticated session (prevents privilege escalation)
        Long childId = resolveChildIdFromSession(httpRequest);

        Long familyId = getSingleFamilyId();
        Exchange exchange = exchangeService.createDirectExchange(request, childId, familyId);

        Map<String, Object> data = toExchangeMap(exchange);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== POST /api/exchanges/blind-box — Blind Box Exchange (Child, Task 5.8) ==========

    @PostMapping("/blind-box")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBlindBoxExchange(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        if (!roles.contains(AuthConstants.ROLE_CHILD)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only children can create exchanges");
        }

        // Verify idempotencyKey is present
        if (!request.containsKey("idempotencyKey") || request.get("idempotencyKey") == null
                || request.get("idempotencyKey").toString().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.EXCHANGE_IDEMPOTENCY_KEY_REQUIRED,
                    "idempotencyKey is required");
        }

        // Derive childId from authenticated session (prevents privilege escalation)
        Long childId = resolveChildIdFromSession(httpRequest);

        Long familyId = getSingleFamilyId();
        Exchange exchange = exchangeService.createBlindBoxExchange(request, childId, familyId);

        Map<String, Object> data = toExchangeMap(exchange);
        ExchangeSnapshot snapshot = exchangeService.getExchangeSnapshot(exchange.getId());
        if (snapshot != null) {
            data.put("snapshot", toSnapshotMap(snapshot));
        }
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== POST /api/exchanges/{id}/fulfill — Fulfill (Parent, Task 5.10) ==========

    @PostMapping("/{id}/fulfill")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fulfillExchange(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only parents can fulfill exchanges");
        }

        Long accountId = getAccountId(httpRequest);
        Long familyId = getSingleFamilyId();

        Exchange exchange = exchangeService.fulfillExchange(id, familyId, accountId);

        Map<String, Object> data = toExchangeMap(exchange);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== POST /api/exchanges/{id}/cancel — Cancel (Parent, Task 5.11) ==========

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelExchange(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);
        Long familyId = getSingleFamilyId();

        // Only parents can cancel
        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only parents can cancel exchanges");
        }

        Exchange exchange = exchangeService.cancelExchange(id, familyId, accountId, roles);

        Map<String, Object> data = toExchangeMap(exchange);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/exchanges — Query Exchanges ==========

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryExchanges(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long childId,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long familyId = getSingleFamilyId();

        // If child, restrict to their own records
        Long viewerChildId = null;
        if (roles.contains(AuthConstants.ROLE_CHILD)) {
            // Child can only see their own exchanges - childId must match their identity
            Long sessionChildId = (Long) httpRequest.getAttribute("currentChildId");
            if (sessionChildId == null) {
                // Fallback: use the childId parameter from request
                if (childId == null) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "Child identity not available");
                }
                viewerChildId = childId;
            } else {
                viewerChildId = sessionChildId;
                if (childId != null && !sessionChildId.equals(childId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN,
                            "Children can only query their own exchanges");
                }
            }
        }

        Map<String, Object> params = new LinkedHashMap<>();
        if (page != null) params.put("page", page);
        if (pageSize != null) params.put("pageSize", pageSize);
        if (type != null) params.put("type", type);
        if (status != null) params.put("status", status);
        if (childId != null) params.put("childId", childId);

        Map<String, Object> data = exchangeService.queryExchanges(params, familyId, viewerChildId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/exchanges/{id} — Get Exchange Detail ==========

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExchange(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long familyId = getSingleFamilyId();

        Exchange exchange = exchangeService.getExchangeById(id, familyId);
        Map<String, Object> data = toExchangeMap(exchange);

        ExchangeSnapshot snapshot = exchangeService.getExchangeSnapshot(exchange.getId());
        if (snapshot != null) {
            data.put("snapshot", toSnapshotMap(snapshot));
        }

        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== Helpers ==========

    /**
     * Derive child ID from the authenticated session (account → child_profile mapping).
     * Prevents privilege escalation by ensuring child users cannot impersonate other children.
     */
    private Long resolveChildIdFromSession(HttpServletRequest httpRequest) {
        // Child session: childId IS the profile ID, validate directly
        Long childId = (Long) httpRequest.getAttribute(AuthConstants.ATTR_CHILD_ID);
        if (childId != null) {
            ChildProfile profile = taskChildMapper.findActiveById(childId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Child profile not found or inactive"));
            return childId;
        }
        // Parent session: resolve via family_member
        Long accountId = getAccountId(httpRequest);
        return taskChildMapper.findByAccountId(accountId)
                .map(ChildProfile::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "No child profile found for session"));
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

    private Long getSingleFamilyId() {
        List<Family> families = familyMapper.selectList(null);
        if (families.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "No family found");
        }
        return families.get(0).getId();
    }

    private Map<String, Object> toExchangeMap(Exchange exchange) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", exchange.getId());
        data.put("childId", exchange.getChildId());
        data.put("familyId", exchange.getFamilyId());
        data.put("type", exchange.getType());
        data.put("status", exchange.getStatus());
        data.put("costPoints", exchange.getCostPoints());
        data.put("prizeId", exchange.getPrizeId());
        data.put("poolId", exchange.getPoolId());
        data.put("resultPrizeId", exchange.getResultPrizeId());
        data.put("idempotencyKey", exchange.getIdempotencyKey());
        data.put("fulfilledAt", exchange.getFulfilledAt());
        data.put("fulfilledBy", exchange.getFulfilledBy());
        data.put("cancelledAt", exchange.getCancelledAt());
        data.put("cancelledBy", exchange.getCancelledBy());
        data.put("createdAt", exchange.getCreatedAt());
        data.put("updatedAt", exchange.getUpdatedAt());
        return data;
    }

    private Map<String, Object> toSnapshotMap(ExchangeSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", snapshot.getId());
        data.put("exchangeId", snapshot.getExchangeId());
        data.put("prizeName", snapshot.getPrizeName());
        data.put("prizeImage", snapshot.getPrizeImage());
        data.put("prizeDescription", snapshot.getPrizeDescription());
        data.put("pointsCost", snapshot.getPointsCost());
        data.put("poolName", snapshot.getPoolName());
        data.put("poolCostPoints", snapshot.getPoolCostPoints());
        data.put("availabilityVersion", snapshot.getAvailabilityVersion());
        data.put("candidateProbabilities", snapshot.getCandidateProbabilities());
        data.put("drawnPrizeName", snapshot.getDrawnPrizeName());
        data.put("drawnPrizeImage", snapshot.getDrawnPrizeImage());
        data.put("drawnProbability", snapshot.getDrawnProbability());
        return data;
    }
}
