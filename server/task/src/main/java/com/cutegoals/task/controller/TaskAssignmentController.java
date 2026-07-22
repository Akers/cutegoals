package com.cutegoals.task.controller;

import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.common.entity.task.TaskAssignment;
import com.cutegoals.task.service.TaskAssignmentService;
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
 * REST controller for task assignment management (Tasks 3.5-3.10).
 */
@RestController
@RequestMapping("/api/task-assignments")
@RequiredArgsConstructor
public class TaskAssignmentController {

    private static final Logger log = LoggerFactory.getLogger(TaskAssignmentController.class);

    private final TaskAssignmentService taskAssignmentService;

    // ========== POST /api/task-assignments — Single Create (Task 3.5) ==========

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAssignment(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskAssignmentService.verifyParentRole(roles);

        Long accountId = getAccountId(httpRequest);
        Long familyId = taskAssignmentService.getSingleFamilyId();

        TaskAssignment assignment = taskAssignmentService.createAssignment(request, familyId, accountId);
        Map<String, Object> data = taskAssignmentService.getAssignmentDetail(
                assignment.getId(), familyId, null);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== POST /api/task-assignments/batch — Batch Create (Task 3.6) ==========

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> createBatchAssignments(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskAssignmentService.verifyParentRole(roles);

        Long accountId = getAccountId(httpRequest);
        Long familyId = taskAssignmentService.getSingleFamilyId();

        List<TaskAssignment> assignments = taskAssignmentService.createBatchAssignments(
                request, familyId, accountId);

        List<Map<String, Object>> data = assignments.stream()
                .map(a -> taskAssignmentService.getAssignmentDetail(a.getId(), familyId, null))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== POST /api/task-assignments/generate-recurring — Recurring (Task 3.7) ==========

    @PostMapping("/generate-recurring")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateRecurring(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskAssignmentService.verifyParentRole(roles);

        Long accountId = getAccountId(httpRequest);
        Long familyId = taskAssignmentService.getSingleFamilyId();

        Map<String, Object> result = taskAssignmentService.generateRecurringAssignments(
                request, familyId, accountId);
        return ResponseEntity.ok(ApiResponse.success(result, requestId));
    }

    // ========== GET /api/task-assignments — List (Task 3.8) ==========

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryAssignments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long childId,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String cancelled,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long familyId = taskAssignmentService.getSingleFamilyId();
        List<String> roles = getRoles(httpRequest);

        // Determine viewer: child can only see own assignments
        Long viewerChildId = null;
        if (roles.contains(AuthConstants.ROLE_CHILD)) {
            // Child view - get childId from assignment context
            // In MVP, child ID is not available from request attributes yet
            // For now, use the childId query param (validated server-side)
            if (childId == null) {
                throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_FORBIDDEN);
            }
            viewerChildId = childId;
        }

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (page != null) params.put("page", page);
        if (pageSize != null) params.put("pageSize", pageSize);
        if (childId != null) params.put("childId", childId);
        if (templateId != null) params.put("templateId", templateId);
        if (status != null) params.put("status", status);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);
        if (taskType != null) params.put("taskType", taskType);
        if (cancelled != null) params.put("cancelled", cancelled);

        Map<String, Object> data = taskAssignmentService.queryAssignments(params, familyId, viewerChildId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/task-assignments/calendar — Calendar (Task 3.8) ==========

    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCalendar(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Long childId,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long familyId = taskAssignmentService.getSingleFamilyId();
        List<String> roles = getRoles(httpRequest);

        Long viewerChildId = null;
        if (roles.contains(AuthConstants.ROLE_CHILD)) {
            if (childId == null) {
                throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_FORBIDDEN);
            }
            viewerChildId = childId;
        }

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("year", String.valueOf(year));
        params.put("month", String.valueOf(month));
        if (childId != null) params.put("childId", childId);

        Map<String, Object> data = taskAssignmentService.getCalendar(params, familyId, viewerChildId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/task-assignments/{id} — Detail ==========

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAssignment(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long familyId = taskAssignmentService.getSingleFamilyId();

        // Check role
        List<String> roles = getRoles(httpRequest);
        Long viewerChildId = null;
        if (roles.contains(AuthConstants.ROLE_CHILD)) {
            viewerChildId = 0L; // Will be validated in service
        }

        Map<String, Object> data = taskAssignmentService.getAssignmentDetail(id, familyId, viewerChildId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== POST /api/task-assignments/{id}/cancel — Cancel (Task 3.9) ==========

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelAssignment(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskAssignmentService.verifyParentRole(roles);

        Long accountId = getAccountId(httpRequest);
        Long familyId = taskAssignmentService.getSingleFamilyId();

        String reason = request != null ? (String) request.get("reason") : null;
        TaskAssignment assignment = taskAssignmentService.cancelAssignment(id, reason, familyId, accountId);

        Map<String, Object> data = taskAssignmentService.getAssignmentDetail(id, familyId, null);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== PUT /api/task-assignments/{id} — Update (Task 3.10) ==========

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAssignment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskAssignmentService.verifyParentRole(roles);

        Long accountId = getAccountId(httpRequest);
        Long familyId = taskAssignmentService.getSingleFamilyId();

        TaskAssignment assignment = taskAssignmentService.updateAssignment(id, request, familyId, accountId);
        Map<String, Object> data = taskAssignmentService.getAssignmentDetail(id, familyId, null);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== PUT /api/task-assignments/{id}/late-policy — Late policy override ==========

    @PutMapping("/{id}/late-policy")
    public ResponseEntity<ApiResponse<Void>> updateLatePolicy(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskAssignmentService.verifyParentRole(roles);

        Long accountId = getAccountId(httpRequest);
        Long familyId = taskAssignmentService.getSingleFamilyId();

        String latePolicy = (String) request.get("latePolicy");
        taskAssignmentService.updateLatePolicy(id, latePolicy, familyId, accountId);
        return ResponseEntity.ok(ApiResponse.success(null, requestId));
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
