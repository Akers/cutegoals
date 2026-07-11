package com.cutegoals.task.controller;

import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.task.service.TaskTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for task template management (Tasks 3.1-3.4).
 */
@RestController
@RequestMapping("/api/task-templates")
@RequiredArgsConstructor
public class TaskTemplateController {

    private static final Logger log = LoggerFactory.getLogger(TaskTemplateController.class);

    private final TaskTemplateService taskTemplateService;

    // ========== POST /api/task-templates — Create (Task 3.1) ==========

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createTemplate(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(httpRequest);
        List<String> roles = getRoles(httpRequest);
        taskTemplateService.verifyParentRole(roles);

        Long familyId = taskTemplateService.getSingleFamilyId();
        var template = taskTemplateService.createTemplate(request, familyId, accountId);

        Map<String, Object> data = taskTemplateService.getTemplateDetail(template.getId(), familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== PUT /api/task-templates/{id} — Update (Task 3.1, 3.3) ==========

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(httpRequest);
        List<String> roles = getRoles(httpRequest);
        taskTemplateService.verifyParentRole(roles);

        Long familyId = taskTemplateService.getSingleFamilyId();
        taskTemplateService.updateTemplate(id, request, familyId, accountId);

        Map<String, Object> data = taskTemplateService.getTemplateDetail(id, familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/task-templates — List with filters (Task 3.1) ==========

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryTemplates(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean includeDeleted,
            @RequestParam(required = false) String keyword,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskTemplateService.verifyParentRole(roles);

        Long familyId = taskTemplateService.getSingleFamilyId();

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (page != null) params.put("page", page);
        if (pageSize != null) params.put("pageSize", pageSize);
        if (category != null) params.put("category", category);
        if (enabled != null) params.put("enabled", enabled);
        if (includeDeleted != null) params.put("includeDeleted", includeDeleted);
        if (keyword != null) params.put("keyword", keyword);

        Map<String, Object> data = taskTemplateService.queryTemplates(params, familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/task-templates/{id} — Detail (Task 3.1) ==========

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTemplate(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskTemplateService.verifyParentRole(roles);

        Long familyId = taskTemplateService.getSingleFamilyId();
        Map<String, Object> data = taskTemplateService.getTemplateDetail(id, familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== DELETE /api/task-templates/{id} — Soft delete (Task 3.4) ==========

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(httpRequest);
        List<String> roles = getRoles(httpRequest);
        taskTemplateService.verifyParentRole(roles);

        Long familyId = taskTemplateService.getSingleFamilyId();
        taskTemplateService.deleteTemplate(id, familyId, accountId);
        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }

    // ========== PUT /api/task-templates/{id}/enabled — Enable/Disable (Task 3.4) ==========

    @PutMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setTemplateEnabled(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(httpRequest);
        List<String> roles = getRoles(httpRequest);
        taskTemplateService.verifyParentRole(roles);

        boolean enabled = Boolean.TRUE.equals(request.get("enabled"));
        Long familyId = taskTemplateService.getSingleFamilyId();
        taskTemplateService.setTemplateEnabled(id, enabled, familyId, accountId);

        Map<String, Object> data = taskTemplateService.getTemplateDetail(id, familyId);
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
}
