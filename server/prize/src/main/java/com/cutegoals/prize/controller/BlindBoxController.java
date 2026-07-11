package com.cutegoals.prize.controller;

import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.entity.prize.BlindBoxItem;
import com.cutegoals.common.entity.prize.BlindBoxPool;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.prize.service.BlindBoxService;
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
import java.util.stream.Collectors;

/**
 * REST controller for blind box pool operations (Phase 5, Tasks 5.3-5.6).
 */
@RestController
@RequestMapping("/api/blind-boxes")
@RequiredArgsConstructor
public class BlindBoxController {

    private static final Logger log = LoggerFactory.getLogger(BlindBoxController.class);

    private final BlindBoxService blindBoxService;
    private final FamilyMapper familyMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPool(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);

        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long familyId = getSingleFamilyId();
        BlindBoxPool pool = blindBoxService.createPool(request, familyId, accountId);

        return ResponseEntity.ok(ApiResponse.success(toPoolMap(pool), requestId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePool(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);

        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long familyId = getSingleFamilyId();
        BlindBoxPool pool = blindBoxService.updatePool(id, request, familyId, accountId);

        return ResponseEntity.ok(ApiResponse.success(toPoolMap(pool), requestId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePool(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);

        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long familyId = getSingleFamilyId();
        blindBoxService.deletePool(id, familyId, accountId);

        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryPools(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean deleted,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long familyId = getSingleFamilyId();

        Map<String, Object> params = new LinkedHashMap<>();
        if (page != null) params.put("page", page);
        if (pageSize != null) params.put("pageSize", pageSize);
        if (enabled != null) params.put("enabled", enabled);
        if (deleted != null) params.put("deleted", deleted);

        Map<String, Object> data = blindBoxService.queryPools(params, familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPool(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long familyId = getSingleFamilyId();
        BlindBoxPool pool = blindBoxService.getPoolById(id, familyId);

        Map<String, Object> data = toPoolMap(pool);
        List<BlindBoxItem> items = blindBoxService.getPoolItems(id, familyId);
        data.put("items", items.stream().map(this::toItemMap).collect(Collectors.toList()));

        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPoolItems(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long familyId = getSingleFamilyId();
        List<BlindBoxItem> items = blindBoxService.getPoolItems(id, familyId);

        List<Map<String, Object>> data = items.stream().map(this::toItemMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addItem(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);

        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long familyId = getSingleFamilyId();
        Long prizeId = request.get("prizeId") != null ? ((Number) request.get("prizeId")).longValue() : null;
        Integer weight = request.get("weight") != null ? ((Number) request.get("weight")).intValue() : null;

        if (prizeId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "prizeId is required");
        }

        BlindBoxItem item = blindBoxService.addItem(id, prizeId, weight, familyId, accountId);

        return ResponseEntity.ok(ApiResponse.success(toItemMap(item), requestId));
    }

    @DeleteMapping("/{poolId}/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @PathVariable Long poolId,
            @PathVariable Long itemId,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long accountId = getAccountId(httpRequest);

        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long familyId = getSingleFamilyId();
        blindBoxService.removeItem(itemId, poolId, familyId, accountId);

        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryAvailablePools(
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

        Map<String, Object> data = blindBoxService.queryAvailablePools(params, familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    @GetMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCandidates(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long familyId = getSingleFamilyId();
        Map<String, Object> data = blindBoxService.getCandidateProbabilities(id, familyId);

        return ResponseEntity.ok(ApiResponse.success(data, requestId));
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

    private Map<String, Object> toPoolMap(BlindBoxPool pool) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", pool.getId());
        data.put("familyId", pool.getFamilyId());
        data.put("name", pool.getName());
        data.put("description", pool.getDescription());
        data.put("costPoints", pool.getCostPoints());
        data.put("enabled", pool.getEnabled());
        data.put("deleted", pool.getDeleted());
        data.put("availabilityVersion", pool.getAvailabilityVersion());
        data.put("createdAt", pool.getCreatedAt());
        data.put("updatedAt", pool.getUpdatedAt());
        return data;
    }

    private Map<String, Object> toItemMap(BlindBoxItem item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", item.getId());
        data.put("poolId", item.getPoolId());
        data.put("prizeId", item.getPrizeId());
        data.put("weight", item.getWeight());
        data.put("createdAt", item.getCreatedAt());
        return data;
    }
}
