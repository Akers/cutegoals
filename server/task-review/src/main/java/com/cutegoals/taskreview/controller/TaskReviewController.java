package com.cutegoals.taskreview.controller;

import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.task.TaskAttempt;
import com.cutegoals.common.entity.task.TaskReview;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.task.mapper.TaskChildMapper;
import com.cutegoals.taskreview.service.TaskReviewService;
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
 * REST controller for task review operations (Phase 4).
 * POST /api/task-review/submissions — submit task (child)
 * POST /api/task-review/{attemptId}/reject — reject (parent)
 * POST /api/task-review/{attemptId}/approve — approve (parent)
 * GET /api/task-review/pending — pending reviews (parent)
 * GET /api/task-review/history — review history (parent/child)
 */
@RestController
@RequestMapping("/api/task-review")
@RequiredArgsConstructor
public class TaskReviewController {

    private static final Logger log = LoggerFactory.getLogger(TaskReviewController.class);

    private final TaskReviewService taskReviewService;
    private final TaskChildMapper taskChildMapper;

    // ========== POST /api/task-review/submissions — Submit (Task 4.1-4.3) ==========

    @PostMapping("/submissions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitTask(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskReviewService.verifyChildRole(roles);

        Long accountId = getAccountId(httpRequest);
        Long familyId = taskReviewService.getSingleFamilyId();

        // For child role, extract childId from assignment context
        Long childId = taskReviewService.extractLong(request, "childId");
        if (childId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "childId is required");
        }

        TaskAttempt attempt = taskReviewService.submitTask(request, childId, familyId, accountId);

        Map<String, Object> data = buildAttemptResponse(attempt);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== POST /api/task-review/{attemptId}/reject — Reject (Task 4.4) ==========

    @PostMapping("/{attemptId}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rejectAttempt(
            @PathVariable Long attemptId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskReviewService.verifyParentRole(roles);

        Long accountId = getAccountId(httpRequest);
        Long familyId = taskReviewService.getSingleFamilyId();

        TaskReview review = taskReviewService.rejectAttempt(attemptId, request, familyId, accountId);

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("reviewId", review.getId());
        data.put("attemptId", review.getAttemptId());
        data.put("decision", review.getDecision());
        data.put("reason", review.getReason());
        data.put("reviewedAt", review.getCreatedAt());

        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== POST /api/task-review/{attemptId}/approve — Approve (Task 4.6-4.7) ==========

    @PostMapping("/{attemptId}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveAttempt(
            @PathVariable Long attemptId,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskReviewService.verifyParentRole(roles);

        Long accountId = getAccountId(httpRequest);
        Long familyId = taskReviewService.getSingleFamilyId();

        Map<String, Object> data = taskReviewService.approveAttempt(attemptId, request, familyId, accountId);

        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/task-review/pending — Pending Reviews (Task 4.10) ==========

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryPendingReviews(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long childId,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskReviewService.verifyParentRole(roles);

        Long familyId = taskReviewService.getSingleFamilyId();

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (page != null) params.put("page", page);
        if (pageSize != null) params.put("pageSize", pageSize);
        if (childId != null) params.put("childId", childId);

        Map<String, Object> data = taskReviewService.queryPendingReviews(params, familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/task-review/history — Review History (Task 4.10) ==========

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryReviewHistory(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long childId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) Long reviewerId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        Long familyId = taskReviewService.getSingleFamilyId();

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (page != null) params.put("page", page);
        if (pageSize != null) params.put("pageSize", pageSize);
        if (childId != null) params.put("childId", childId);
        if (decision != null) params.put("decision", decision);
        if (reviewerId != null) params.put("reviewerId", reviewerId);
        if (startDate != null) params.put("startDate", startDate);
        if (endDate != null) params.put("endDate", endDate);

        Long viewerChildId = null;
        if (roles.contains(AuthConstants.ROLE_CHILD)) {
            if (childId == null) {
                throw new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN);
            }
            // Child role: derive viewerChildId from session, not URL
            Long accountId = getAccountId(httpRequest);
            Long sessionChildId = taskChildMapper.findByAccountId(accountId)
                    .map(ChildProfile::getId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN, "No child profile found for session"));
            if (!sessionChildId.equals(childId)) {
                throw new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN, "Child identity mismatch");
            }
            viewerChildId = sessionChildId;
        } else {
            taskReviewService.verifyParentRole(roles);
        }

        Map<String, Object> data = taskReviewService.queryReviewHistory(params, familyId, viewerChildId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== GET /api/task-review/child/{childId}/history — Child's own history ==========

    @GetMapping("/child/{childId}/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryChildHistory(
            @PathVariable Long childId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpServletRequest httpRequest) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<String> roles = getRoles(httpRequest);
        taskReviewService.verifyChildRole(roles);

        Long familyId = taskReviewService.getSingleFamilyId();

        // Validate childId belongs to this family (child in a family can only see own family's data)
        Long accountId = getAccountId(httpRequest);
        Long sessionChildId = taskChildMapper.findByAccountId(accountId)
                .map(ChildProfile::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN, "No child profile found for session"));
        if (!sessionChildId.equals(childId)) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN, "Child identity mismatch");
        }

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (page != null) params.put("page", page);
        if (pageSize != null) params.put("pageSize", pageSize);

        Map<String, Object> data = taskReviewService.queryChildHistory(childId, params, familyId);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    // ========== Helpers ==========

    private Map<String, Object> buildAttemptResponse(TaskAttempt attempt) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", attempt.getId());
        data.put("assignmentId", attempt.getAssignmentId());
        data.put("childId", attempt.getChildId());
        data.put("attemptNumber", attempt.getAttemptNumber());
        data.put("content", attempt.getContent());
        data.put("attachments", attempt.getAttachments());
        data.put("submittedAt", attempt.getSubmittedAt());
        data.put("isLate", attempt.getIsLate());
        data.put("latePolicyApplied", attempt.getLatePolicyApplied());
        return data;
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
