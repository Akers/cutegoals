package com.cutegoals.prize.controller;

import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.entity.prize.Prize;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.prize.service.PrizeService;
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
 * REST controller for prize operations (Phase 5, Tasks 5.1-5.2).
 * Parent endpoints: /api/prizes/**
 * Child endpoints: /api/prizes/available/**
 */
@RestController
@RequestMapping("/api/prizes")
@RequiredArgsConstructor
public class PrizeController {

    private static final Logger log = LoggerFactory.getLogger(PrizeController.class);

    private final PrizeService prizeService;
    private final FamilyMapper familyMapper;

    // ========== POST /api/prizes — Create Prize (Parent) ==========

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPrize(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);

        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only parents can create prizes");
        }

        Long familyId = getSingleFamilyId();
        Prize prize = prizeService.createPrize(request, familyId, accountId);

        Map<String, Object> data = toPrizeMap(prize);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== PUT /api/prizes/{id} — Update Prize (Parent) ==========

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePrize(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);

        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only parents can update prizes");
        }

        Long familyId = getSingleFamilyId();
        Prize prize = prizeService.updatePrize(id, request, familyId, accountId);

        Map<String, Object> data = toPrizeMap(prize);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== DELETE /api/prizes/{id} — Soft Delete Prize (Parent) ==========

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePrize(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);

        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only parents can delete prizes");
        }

        Long familyId = getSingleFamilyId();
        prizeService.deletePrize(id, familyId, accountId);

        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }

    // ========== GET /api/prizes — Query Prizes (Parent) ==========

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryPrizes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean deleted,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only parents can view prize management");
        }

        Long familyId = getSingleFamilyId();

        Map<String, Object> params = new LinkedHashMap<>();
        if (page != null) params.put("page", page);
        if (pageSize != null) params.put("pageSize", pageSize);
        if (enabled != null) params.put("enabled", enabled);
        if (deleted != null) params.put("deleted", deleted);

        Map<String, Object> data = prizeService.queryPrizes(params, familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/prizes/{id} — Get Prize Detail (Parent/Child) ==========

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPrize(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long familyId = getSingleFamilyId();

        Prize prize;
        if (roles.contains(AuthConstants.ROLE_CHILD)) {
            prize = prizeService.getAvailablePrizeById(id, familyId);
        } else {
            prize = prizeService.getPrizeById(id, familyId);
        }

        Map<String, Object> data = toPrizeMap(prize);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/prizes/available — Child Available Prizes ==========

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryAvailablePrizes(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        if (!roles.contains(AuthConstants.ROLE_CHILD)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long familyId = getSingleFamilyId();

        Map<String, Object> params = new LinkedHashMap<>();
        if (page != null) params.put("page", page);
        if (pageSize != null) params.put("pageSize", pageSize);

        Map<String, Object> data = prizeService.queryAvailablePrizes(params, familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== PUT /api/prizes/{id}/stock — Adjust Stock (Parent) ==========

    @PutMapping("/{id}/stock")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adjustStock(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);

        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only parents can adjust stock");
        }

        Long familyId = getSingleFamilyId();
        Integer newStock = request.get("stock") != null ? ((Number) request.get("stock")).intValue() : null;

        Prize prize = prizeService.adjustStock(id, newStock, familyId, accountId);

        return ResponseEntity.ok(ApiResponse.success(toPrizeMap(prize), requestId));
    }

    // ========== Helpers ==========

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

    private Map<String, Object> toPrizeMap(Prize prize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", prize.getId());
        data.put("familyId", prize.getFamilyId());
        data.put("name", prize.getName());
        data.put("description", prize.getDescription());
        data.put("image", prize.getImage());
        data.put("pointsCost", prize.getPointsCost());
        data.put("stock", prize.getStock());
        data.put("enabled", prize.getEnabled());
        data.put("deleted", prize.getDeleted());
        data.put("createdAt", prize.getCreatedAt());
        data.put("updatedAt", prize.getUpdatedAt());
        return data;
    }
}
