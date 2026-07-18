package com.cutegoals.taskreview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.points.PointsBalance;
import com.cutegoals.common.entity.points.PointsLedger;
import com.cutegoals.common.entity.task.TaskAssignment;
import com.cutegoals.common.entity.task.TaskAssignmentSnapshot;
import com.cutegoals.common.entity.task.TaskAttempt;
import com.cutegoals.common.entity.task.TaskReview;
import com.cutegoals.common.entity.task.TaskTemplate;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.points.mapper.PointsBalanceMapper;
import com.cutegoals.points.mapper.PointsLedgerMapper;
import com.cutegoals.task.mapper.TaskAssignmentMapper;
import com.cutegoals.task.mapper.TaskAssignmentSnapshotMapper;
import com.cutegoals.task.mapper.TaskChildMapper;
import com.cutegoals.task.mapper.TaskTemplateMapper;
import com.cutegoals.task.service.TaskTemplateFrequencyService;
import com.cutegoals.taskreview.mapper.TaskAttemptMapper;
import com.cutegoals.taskreview.mapper.TaskReviewMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for task submission, review, approval, and rejection (Phase 4).
 */
@Service
@RequiredArgsConstructor
public class TaskReviewService {

    private static final Logger log = LoggerFactory.getLogger(TaskReviewService.class);

    private final TaskAttemptMapper taskAttemptMapper;
    private final TaskReviewMapper taskReviewMapper;
    private final TaskAssignmentMapper taskAssignmentMapper;
    private final TaskChildMapper taskChildMapper;
    private final FamilyMapper familyMapper;
    private final PointsLedgerMapper pointsLedgerMapper;
    private final PointsBalanceMapper pointsBalanceMapper;
    private final AuditService auditService;
    private final TaskTemplateMapper taskTemplateMapper;
    private final TaskAssignmentSnapshotMapper taskAssignmentSnapshotMapper;
    private final TaskTemplateFrequencyService taskTemplateFrequencyService;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    // ========== Task 4.1-4.2: Submission with Idempotency ==========

    /**
     * Submit a task attempt for a given assignment.
     * Validates ownership, state, late policy, and idempotency.
     */
    @Transactional
    public TaskAttempt submitTask(Map<String, Object> request, Long childId, Long familyId, Long accountId) {
        Long assignmentId = extractLong(request, "assignmentId");
        String content = extractString(request, "content");
        String idempotencyKey = extractAndValidateIdempotencyKey(request);

        // Validate content limits
        if (content != null && content.length() > 2000) {
            throw new BusinessException(ErrorCode.TASK_SUBMISSION_VALIDATION_FAILED,
                    "Content must not exceed 2000 characters");
        }

        // Validate attachments
        String attachments = extractString(request, "attachments");
        if (attachments != null && attachments.length() > 0) {
            try {
                @SuppressWarnings("unchecked")
                List<String> attachmentList = (List<String>) request.get("attachments");
                if (attachmentList != null) {
                    if (attachmentList.size() > 10) {
                        throw new BusinessException(ErrorCode.TASK_SUBMISSION_VALIDATION_FAILED,
                                "Attachments must not exceed 10 items");
                    }
                    for (String att : attachmentList) {
                        if (att != null && att.length() > 500) {
                            throw new BusinessException(ErrorCode.TASK_SUBMISSION_VALIDATION_FAILED,
                                    "Each attachment must not exceed 500 characters");
                        }
                    }
                }
            } catch (ClassCastException e) {
                // attachments might be a JSON string, validate length
                if (attachments.length() > 5000) {
                    throw new BusinessException(ErrorCode.TASK_SUBMISSION_VALIDATION_FAILED,
                            "Attachments too large");
                }
            }
        }

        // Check idempotency
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            Optional<TaskAttempt> existing = taskAttemptMapper.findByIdempotencyKey(childId, idempotencyKey);
            if (existing.isPresent()) {
                TaskAttempt existingAttempt = existing.get();
                // Verify content match
                if (!isSameContent(content, attachments, existingAttempt)) {
                    throw new BusinessException(ErrorCode.TASK_SUBMISSION_IDEMPOTENCY_CONFLICT,
                            "Idempotency key used with different content");
                }
                return existingAttempt;
            }
        }

        // Fetch assignment with lock
        TaskAssignment assignment = taskAssignmentMapper.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND));

        // Verify family
        if (!assignment.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN,
                    "Assignment does not belong to your family");
        }

        // Verify child owns this assignment
        if (!assignment.getChildId().equals(childId)) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN,
                    "You can only submit your own tasks");
        }

        // Check if cancelled
        if (Boolean.TRUE.equals(assignment.getCancelled())) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_CANCELLED,
                    "Cannot submit a cancelled assignment");
        }

        // Check current status
        String status = assignment.getStatus();
        if ("SUBMITTED".equals(status)) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_INVALID_STATE,
                    "Task is already submitted");
        }
        if ("APPROVED".equals(status)) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_INVALID_STATE,
                    "Task is already approved");
        }

        // Task 4.3: Late policy check
        LocalDateTime now = LocalDateTime.now();
        boolean isLate = now.isAfter(assignment.getDeadline());
        String latePolicy = assignment.getLatePolicy();

        if (isLate && "REJECT".equals(latePolicy)) {
            throw new BusinessException(ErrorCode.TASK_SUBMISSION_LATE_NOT_ALLOWED,
                    "Late submissions are not allowed for this task");
        }

        // Task 11.3: LIMITED type date window check
        TaskTemplate template = taskTemplateMapper.findById(assignment.getTemplateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_TEMPLATE_NOT_FOUND));
        if ("LIMITED".equals(template.getTaskType())) {
            LocalDateTime limitedStart = parseLimitedDate(template.getTypeConfig(), "start_date");
            LocalDateTime limitedEnd = parseLimitedDate(template.getTypeConfig(), "end_date");
            if (limitedStart != null && now.isBefore(limitedStart)) {
                throw new BusinessException(ErrorCode.TASK_LIMITED_NOT_STARTED,
                        "This LIMITED task has not started yet (starts at: " + limitedStart + ")");
            }
            if (limitedEnd != null && now.isAfter(limitedEnd)) {
                throw new BusinessException(ErrorCode.TASK_LIMITED_EXPIRED,
                        "This LIMITED task has expired (ended at: " + limitedEnd + ")");
            }
        }
        // STANDING: check if max submissions reached
        if ("STANDING".equals(template.getTaskType())) {
            Integer currentCount = assignment.getSubmissionCount();
            Integer maxSubmissions = parseMaxSubmissions(template.getTypeConfig());
            if (maxSubmissions != null && currentCount != null && currentCount >= maxSubmissions) {
                throw new BusinessException(ErrorCode.TASK_STANDING_LIMIT_REACHED,
                        "Maximum submissions reached for this STANDING task");
            }
        }

        // Determine next attempt number
        int maxAttempt = taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId);
        int attemptNumber = maxAttempt + 1;

        // Create the attempt
        TaskAttempt attempt = new TaskAttempt();
        attempt.setAssignmentId(assignmentId);
        attempt.setChildId(childId);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setContent(content);
        attempt.setAttachments(attachments);
        attempt.setSubmittedAt(now);
        attempt.setIsLate(isLate);
        attempt.setLatePolicyApplied(latePolicy);
        attempt.setIdempotencyKey(idempotencyKey);

        try {
            taskAttemptMapper.insert(attempt);
        } catch (DuplicateKeyException e) {
            // Idempotency key collision - another concurrent insert with same key
            if (idempotencyKey != null) {
                Optional<TaskAttempt> existing = taskAttemptMapper.findByIdempotencyKey(childId, idempotencyKey);
                if (existing.isPresent()) {
                    return existing.get();
                }
            }
            throw new BusinessException(ErrorCode.TASK_REVIEW_INVALID_STATE,
                    "Concurrent submission detected, please retry");
        }

        // Update assignment status
        assignment.setStatus("SUBMITTED");
        taskAssignmentMapper.updateById(assignment);

        auditService.record(AuditEvent.TASK_SUBMITTED, accountId, "SUCCESS",
                "Task submitted: assignmentId=" + assignmentId + ", attempt=" + attemptNumber
                        + ", late=" + isLate);

        log.info("Task submitted: assignmentId={}, childId={}, attempt={}, late={}",
                assignmentId, childId, attemptNumber, isLate);

        return attempt;
    }

    // ========== Task 4.4: Rejection ==========

    /**
     * Reject a task attempt. Requires non-empty reason 1-1000 chars.
     */
    @Transactional
    public TaskReview rejectAttempt(Long attemptId, Map<String, Object> request, Long familyId, Long accountId) {
        String reason = extractString(request, "reason");
        String idempotencyKey = extractAndValidateIdempotencyKey(request);

        // Validate reason
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_REASON_REQUIRED,
                    "Rejection reason is required");
        }
        reason = reason.trim();
        if (reason.length() < 1 || reason.length() > 1000) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_VALIDATION_FAILED,
                    "Rejection reason must be between 1 and 1000 characters");
        }

        // Idempotency check
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            Optional<TaskReview> existing = taskReviewMapper.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                TaskReview er = existing.get();
                if (!"REJECTED".equals(er.getDecision())) {
                    throw new BusinessException(ErrorCode.TASK_REVIEW_IDEMPOTENCY_CONFLICT,
                            "Idempotency key used with different decision");
                }
                // Check reason matches
                if (!reason.equals(er.getReason())) {
                    throw new BusinessException(ErrorCode.TASK_REVIEW_IDEMPOTENCY_CONFLICT,
                            "Idempotency key used with different reason");
                }
                return er;
            }
        }

        // Fetch attempt with lock
        TaskAttempt attempt = taskAttemptMapper.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_REVIEW_NOT_FOUND,
                        "Attempt not found"));

        // Fetch assignment
        TaskAssignment assignment = taskAssignmentMapper.findById(attempt.getAssignmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND));

        // Verify family
        if (!assignment.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN);
        }

        // Check if cancelled
        if (Boolean.TRUE.equals(assignment.getCancelled())) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_CANCELLED);
        }

        // Check if attempt is stale (not the current latest attempt)
        int maxAttempt = taskAttemptMapper.getMaxAttemptNumber(attempt.getAssignmentId(), attempt.getChildId());
        if (attempt.getAttemptNumber() < maxAttempt) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_STALE_ATTEMPT,
                    "This attempt is no longer the current submission");
        }

        // Check if already decided
        Optional<TaskReview> existingReview = taskReviewMapper.findByAttemptIdForUpdate(attemptId);
        if (existingReview.isPresent()) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_ALREADY_DECIDED,
                    "This attempt has already been reviewed");
        }

        // Create rejection review record
        TaskReview review = new TaskReview();
        review.setAttemptId(attemptId);
        review.setAssignmentId(attempt.getAssignmentId());
        review.setReviewerId(accountId);
        review.setDecision("REJECTED");
        review.setReason(reason);
        review.setIdempotencyKey(idempotencyKey);
        try {
            taskReviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // Unique constraint on (attempt_id) ensures at most one review per attempt
            throw new BusinessException(ErrorCode.TASK_REVIEW_ALREADY_DECIDED,
                    "This attempt has already been reviewed");
        }

        // Update assignment status to REJECTED
        assignment.setStatus("REJECTED");
        taskAssignmentMapper.updateById(assignment);

        auditService.record(AuditEvent.TASK_REVIEW_REJECTED, accountId, "SUCCESS",
                "Task attempt rejected: attemptId=" + attemptId + ", reason=" + reason);

        log.info("Task attempt rejected: attemptId={}, assignmentId={}, reason={}",
                attemptId, attempt.getAssignmentId(), reason);

        return review;
    }

    // ========== Task 4.6-4.7: Approval with Points in Same Transaction ==========

    /**
     * Approve a task attempt. Creates APPROVED review + EARN points ledger + updates balance
     * in the same transaction. Enforces single approval per attempt via concurrency control.
     */
    @Transactional
    public Map<String, Object> approveAttempt(Long attemptId, Map<String, Object> request,
                                               Long familyId, Long accountId) {
        String reason = extractString(request, "reason");
        String idempotencyKey = extractAndValidateIdempotencyKey(request);

        // Validate reason (optional for approval)
        if (reason != null && reason.length() > 1000) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_VALIDATION_FAILED,
                    "Approval reason must not exceed 1000 characters");
        }

        // Idempotency check
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            Optional<TaskReview> existing = taskReviewMapper.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                TaskReview er = existing.get();
                if (!"APPROVED".equals(er.getDecision())) {
                    throw new BusinessException(ErrorCode.TASK_REVIEW_IDEMPOTENCY_CONFLICT,
                            "Idempotency key used with different decision");
                }
                // Return existing result
                return buildApprovalResult(er, attemptId);
            }
        }

        // Fetch attempt with lock
        TaskAttempt attempt = taskAttemptMapper.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_REVIEW_NOT_FOUND,
                        "Attempt not found"));

        // Fetch assignment
        TaskAssignment assignment = taskAssignmentMapper.findById(attempt.getAssignmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND));

        // Verify family
        if (!assignment.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN);
        }

        // Check if cancelled
        if (Boolean.TRUE.equals(assignment.getCancelled())) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_CANCELLED);
        }

        // Check if attempt is stale
        int maxAttempt = taskAttemptMapper.getMaxAttemptNumber(attempt.getAssignmentId(), attempt.getChildId());
        if (attempt.getAttemptNumber() < maxAttempt) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_STALE_ATTEMPT,
                    "This attempt is no longer the current submission");
        }

        // Check if already decided (concurrency control)
        Optional<TaskReview> existingReview = taskReviewMapper.findByAttemptIdForUpdate(attemptId);
        if (existingReview.isPresent()) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_ALREADY_DECIDED,
                    "This attempt has already been reviewed");
        }

        // Use assignment snapshot reward (not current template value)
        Integer rewardPoints = assignment.getSnapshotDifficultyReward();
        if (rewardPoints == null || rewardPoints <= 0) {
            rewardPoints = 0;
        }

        // Create APPROVED review
        TaskReview review = new TaskReview();
        review.setAttemptId(attemptId);
        review.setAssignmentId(attempt.getAssignmentId());
        review.setReviewerId(accountId);
        review.setDecision("APPROVED");
        review.setReason(reason);
        review.setIdempotencyKey(idempotencyKey);
        try {
            taskReviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // Unique constraint on (attempt_id) ensures at most one review per attempt
            throw new BusinessException(ErrorCode.TASK_REVIEW_ALREADY_DECIDED,
                    "This attempt has already been reviewed");
        }

        // Fetch template for task-type-specific logic
        TaskTemplate template = taskTemplateMapper.findById(assignment.getTemplateId())
                .orElse(null);

        // Task 11.4: REPEAT type — complete current assignment, create next period
        boolean repeatHandled = false;
        if (template != null && "REPEAT".equals(template.getTaskType())) {
            assignment.setStatus("COMPLETED");
            taskAssignmentMapper.updateById(assignment);

            LocalDate fromDate = assignment.getDeadline() != null
                    ? assignment.getDeadline().toLocalDate()
                    : LocalDate.now();
            Optional<LocalDate> nextDate = taskTemplateFrequencyService.nextTriggerDate(
                    template.getTypeConfig(), fromDate);

            if (nextDate.isPresent()) {
                createRepeatAssignment(template, assignment, nextDate.get(), familyId);
                auditService.record(AuditEvent.REPEAT_ASSIGNMENT_CREATED, accountId, "SUCCESS",
                        "REPEAT next period created: templateId=" + template.getId()
                                + ", childId=" + assignment.getChildId()
                                + ", nextDate=" + nextDate.get());
            }
            repeatHandled = true;
            log.info("REPEAT task completed: assignmentId={}, nextTrigger={}",
                    assignment.getId(), nextDate.orElse(null));
        }

        // Task 11.3: STANDING type — increment submission_count
        if (!repeatHandled) {
            assignment.setStatus("APPROVED");

            boolean standingCompleted = false;
            if (template != null && "STANDING".equals(template.getTaskType())) {
                int currentCount = assignment.getSubmissionCount() != null ? assignment.getSubmissionCount() : 0;
                int newCount = currentCount + 1;
                assignment.setSubmissionCount(newCount);
                Integer maxSubmissions = parseMaxSubmissions(template.getTypeConfig());
                if (maxSubmissions != null && newCount >= maxSubmissions) {
                    assignment.setStatus("COMPLETED");
                    standingCompleted = true;
                }
            }

            taskAssignmentMapper.updateById(assignment);

            if (standingCompleted) {
                log.info("STANDING task completed: assignmentId={}, submissions={}",
                        assignment.getId(), assignment.getSubmissionCount());
            }
        }

        // Create EARN points ledger entry if reward > 0
        if (rewardPoints > 0) {
            String businessRef = "ATTEMPT_" + attemptId;
            Long childId = attempt.getChildId();

            // Check ledger idempotency for this business ref
            Optional<PointsLedger> existingLedger = pointsLedgerMapper.findByBusinessRef(childId, businessRef);
            if (existingLedger.isEmpty()) {
                // Fetch balance with lock（防御性兜底：积分账户不存在则创建）
                PointsBalance balance = pointsBalanceMapper.findByChildIdForUpdate(childId)
                        .orElseGet(() -> {
                            PointsBalance nb = new PointsBalance();
                            nb.setChildId(childId);
                            nb.setBalance(0);
                            nb.setTotalEarned(0);
                            pointsBalanceMapper.insert(nb);
                            log.warn("Lazy-created points balance for childId={}", childId);
                            // 重新查询以获取 version 等数据库默认值
                            return pointsBalanceMapper.findByChildIdForUpdate(childId)
                                    .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_ACCOUNT_NOT_FOUND,
                                            "Points account not found for child: " + childId));
                        });

                int newBalance = balance.getBalance() + rewardPoints;
                int newTotalEarned = balance.getTotalEarned() + rewardPoints;

                // Build source snapshot
                String sourceSnapshot = String.format(
                        "{\"templateName\":\"%s\",\"difficultyName\":\"%s\",\"rewardPoints\":%d}",
                        assignment.getSnapshotTemplateName() != null ? escapeJson(assignment.getSnapshotTemplateName()) : "",
                        assignment.getSnapshotDifficultyName() != null ? escapeJson(assignment.getSnapshotDifficultyName()) : "",
                        rewardPoints);

                // Create ledger entry
                PointsLedger ledger = new PointsLedger();
                ledger.setChildId(childId);
                ledger.setType("EARN");
                ledger.setAmount(rewardPoints);
                ledger.setBalanceAfter(newBalance);
                ledger.setBusinessRef(businessRef);
                ledger.setSourceSnapshot(sourceSnapshot);
                ledger.setOperatorId(accountId);
                try {
                    pointsLedgerMapper.insert(ledger);
                } catch (DuplicateKeyException e) {
                    // Unique constraint on (child_id, business_ref) prevents duplicate EARN entries
                    throw new BusinessException(ErrorCode.POINTS_REFERENCE_CONFLICT,
                            "Points ledger entry already exists for this business reference");
                }

                // Update balance with optimistic lock
                int updated = pointsBalanceMapper.updateBalanceWithVersion(
                        childId, newBalance, newTotalEarned, balance.getVersion());
                if (updated == 0) {
                    throw new BusinessException(ErrorCode.POINTS_ACCOUNT_CONFLICT,
                            "Points balance was modified concurrently, transaction rolled back");
                }

                auditService.record(AuditEvent.POINTS_EARN, accountId, "SUCCESS",
                        "Points earned: childId=" + childId + ", amount=" + rewardPoints
                                + ", attemptId=" + attemptId);
            }
        }

        auditService.record(AuditEvent.TASK_REVIEW_APPROVED, accountId, "SUCCESS",
                "Task attempt approved: attemptId=" + attemptId + ", reward=" + rewardPoints);

        log.info("Task attempt approved: attemptId={}, assignmentId={}, reward={}",
                attemptId, attempt.getAssignmentId(), rewardPoints);

        return buildApprovalResult(review, attemptId);
    }

    // ========== Task 4.10: Query Methods ==========

    /**
     * Query pending submissions for review (parent view).
     * Returns un-cancelled SUBMITTED tasks sorted by submitted_at ASC, attempt id ASC.
     */
    public Map<String, Object> queryPendingReviews(Map<String, Object> params, Long familyId) {
        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_INVALID_QUERY,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        // Get all SUBMITTED, not cancelled assignments for this family
        LambdaQueryWrapper<TaskAssignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskAssignment::getFamilyId, familyId)
                .eq(TaskAssignment::getStatus, "SUBMITTED")
                .eq(TaskAssignment::getCancelled, false);

        // Optional child filter
        if (params.containsKey("childId")) {
            wrapper.eq(TaskAssignment::getChildId, ((Number) params.get("childId")).longValue());
        }

        // Order by deadline asc, id asc
        wrapper.orderByAsc(TaskAssignment::getDeadline);
        wrapper.orderByAsc(TaskAssignment::getId);

        Page<TaskAssignment> page = taskAssignmentMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        List<Map<String, Object>> enriched = new ArrayList<>();
        for (TaskAssignment assignment : page.getRecords()) {
            Map<String, Object> item = enrichAssignmentWithAttempts(assignment);
            enriched.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", enriched);
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalElements", page.getTotal());
        result.put("totalPages", page.getPages());
        return result;
    }

    /**
     * Query review history. Supports filtering by child, decision, reviewer, date range.
     */
    public Map<String, Object> queryReviewHistory(Map<String, Object> params, Long familyId, Long viewerChildId) {
        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_INVALID_QUERY,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        // Validate date range
        if (params.containsKey("startDate") && params.containsKey("endDate")) {
            String startStr = (String) params.get("startDate");
            String endStr = (String) params.get("endDate");
            LocalDate startDate = LocalDate.parse(startStr);
            LocalDate endDate = LocalDate.parse(endStr);
            if (startDate.isAfter(endDate)) {
                throw new BusinessException(ErrorCode.TASK_REVIEW_INVALID_QUERY,
                        "Start date must not be after end date");
            }
        }

        // Build assignment query for family
        LambdaQueryWrapper<TaskAssignment> assignmentWrapper = new LambdaQueryWrapper<>();
        assignmentWrapper.eq(TaskAssignment::getFamilyId, familyId);

        if (viewerChildId != null) {
            assignmentWrapper.eq(TaskAssignment::getChildId, viewerChildId);
        } else if (params.containsKey("childId")) {
            assignmentWrapper.eq(TaskAssignment::getChildId, ((Number) params.get("childId")).longValue());
        }

        List<TaskAssignment> familyAssignments = taskAssignmentMapper.selectList(assignmentWrapper);
        if (familyAssignments.isEmpty()) {
            Map<String, Object> emptyResult = new LinkedHashMap<>();
            emptyResult.put("content", List.of());
            emptyResult.put("page", 1);
            emptyResult.put("pageSize", pageSize);
            emptyResult.put("totalElements", 0);
            emptyResult.put("totalPages", 0);
            return emptyResult;
        }

        Set<Long> assignmentIds = familyAssignments.stream()
                .map(TaskAssignment::getId).collect(Collectors.toSet());

        // Build review query
        LambdaQueryWrapper<TaskReview> reviewWrapper = new LambdaQueryWrapper<>();
        reviewWrapper.in(TaskReview::getAssignmentId, assignmentIds);

        if (params.containsKey("decision")) {
            reviewWrapper.eq(TaskReview::getDecision, params.get("decision"));
        }

        if (params.containsKey("reviewerId")) {
            reviewWrapper.eq(TaskReview::getReviewerId, ((Number) params.get("reviewerId")).longValue());
        }

        // Date range filter on review created_at
        if (params.containsKey("startDate") && params.containsKey("endDate")) {
            String startStr = (String) params.get("startDate");
            String endStr = (String) params.get("endDate");
            LocalDate startDate = LocalDate.parse(startStr);
            LocalDate endDate = LocalDate.parse(endStr);

            // Use Asia/Shanghai timezone for date boundaries
            ZoneId shanghaiZone = ZoneId.of("Asia/Shanghai");
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

            reviewWrapper.ge(TaskReview::getCreatedAt, startDateTime);
            reviewWrapper.lt(TaskReview::getCreatedAt, endDateTime);
        }

        reviewWrapper.orderByDesc(TaskReview::getCreatedAt);
        reviewWrapper.orderByDesc(TaskReview::getId);

        Page<TaskReview> reviewPage = taskReviewMapper.selectPage(
                new Page<>(pageNum, pageSize), reviewWrapper);

        // Enrich with assignment and attempt data
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (TaskReview review : reviewPage.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reviewId", review.getId());
            item.put("attemptId", review.getAttemptId());
            item.put("assignmentId", review.getAssignmentId());
            item.put("reviewerId", review.getReviewerId());
            item.put("decision", review.getDecision());
            item.put("reason", review.getReason());
            item.put("reviewedAt", review.getCreatedAt());

            // Enrich with assignment snapshot data
            TaskAssignment assn = familyAssignments.stream()
                    .filter(a -> a.getId().equals(review.getAssignmentId()))
                    .findFirst().orElse(null);
            if (assn != null) {
                item.put("childId", assn.getChildId());
                item.put("snapshotTemplateName", assn.getSnapshotTemplateName());
                item.put("snapshotDifficultyName", assn.getSnapshotDifficultyName());
                item.put("snapshotDifficultyReward", assn.getSnapshotDifficultyReward());
            }

            // Enrich with attempt data
            Optional<TaskAttempt> attempt = taskAttemptMapper.findById(review.getAttemptId());
            attempt.ifPresent(a -> {
                item.put("attemptNumber", a.getAttemptNumber());
                item.put("isLate", a.getIsLate());
                item.put("submittedAt", a.getSubmittedAt());
                item.put("content", a.getContent());
            });

            enriched.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", enriched);
        result.put("page", reviewPage.getCurrent());
        result.put("pageSize", reviewPage.getSize());
        result.put("totalElements", reviewPage.getTotal());
        result.put("totalPages", reviewPage.getPages());
        return result;
    }

    /**
     * Query child's own attempts and reviews.
     */
    public Map<String, Object> queryChildHistory(Long childId, Map<String, Object> params, Long familyId) {
        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_INVALID_QUERY,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        // Get assignments for this child in family
        LambdaQueryWrapper<TaskAssignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskAssignment::getFamilyId, familyId)
                .eq(TaskAssignment::getChildId, childId)
                .orderByDesc(TaskAssignment::getDeadline)
                .orderByDesc(TaskAssignment::getId);

        Page<TaskAssignment> page = taskAssignmentMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        List<Map<String, Object>> enriched = new ArrayList<>();
        for (TaskAssignment assignment : page.getRecords()) {
            Map<String, Object> item = enrichAssignmentWithAttempts(assignment);
            enriched.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", enriched);
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalElements", page.getTotal());
        result.put("totalPages", page.getPages());
        return result;
    }

    // ========== Task 11.4: REPEAT assignment creation ==========

    private void createRepeatAssignment(TaskTemplate template, TaskAssignment completed,
                                         LocalDate nextDate, Long familyId) {
        String occurrenceKey = familyId + "_" + completed.getChildId() + "_"
                + template.getId() + "_" + nextDate;

        // Check if already exists
        if (taskAssignmentMapper.countByOccurrenceKey(occurrenceKey) > 0) {
            log.debug("REPEAT assignment already exists for occurrenceKey={}", occurrenceKey);
            return;
        }

        TaskAssignment next = new TaskAssignment();
        next.setFamilyId(familyId);
        next.setTemplateId(template.getId());
        next.setChildId(completed.getChildId());
        next.setDifficultyId(completed.getDifficultyId());
        next.setDeadline(nextDate.atTime(23, 59, 59));
        next.setStatus("PENDING_OPEN");
        next.setLatePolicy(completed.getLatePolicy());
        next.setCancelled(false);
        next.setVersion(1);
        next.setOccurrenceKey(occurrenceKey);
        next.setSubmissionCount(0);

        // Snapshot fields from completed assignment
        next.setSnapshotTemplateName(completed.getSnapshotTemplateName());
        next.setSnapshotTemplateDescription(completed.getSnapshotTemplateDescription());
        next.setSnapshotTemplateCategory(completed.getSnapshotTemplateCategory());
        next.setSnapshotTemplateIcon(completed.getSnapshotTemplateIcon());
        next.setSnapshotDifficultyName(completed.getSnapshotDifficultyName());
        next.setSnapshotDifficultyReward(completed.getSnapshotDifficultyReward());

        taskAssignmentMapper.insert(next);

        // Snapshot table for history
        TaskAssignmentSnapshot snapshot = new TaskAssignmentSnapshot();
        snapshot.setAssignmentId(next.getId());
        snapshot.setTemplateName(next.getSnapshotTemplateName());
        snapshot.setTemplateDescription(next.getSnapshotTemplateDescription());
        snapshot.setTemplateCategory(next.getSnapshotTemplateCategory());
        snapshot.setTemplateIcon(next.getSnapshotTemplateIcon());
        snapshot.setDifficultyName(next.getSnapshotDifficultyName());
        snapshot.setDifficultyRewardPoints(next.getSnapshotDifficultyReward());
        taskAssignmentSnapshotMapper.insert(snapshot);

        log.info("REPEAT next period created: assignmentId={}, nextDate={}, occurrenceKey={}",
                next.getId(), nextDate, occurrenceKey);
    }

    // ========== Helpers ==========

    private Map<String, Object> enrichAssignmentWithAttempts(TaskAssignment assignment) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", assignment.getId());
        item.put("childId", assignment.getChildId());
        item.put("templateId", assignment.getTemplateId());
        item.put("difficultyId", assignment.getDifficultyId());
        item.put("deadline", assignment.getDeadline());
        item.put("status", assignment.getStatus());
        item.put("latePolicy", assignment.getLatePolicy());
        item.put("cancelled", assignment.getCancelled());
        item.put("version", assignment.getVersion());
        item.put("snapshotTemplateName", assignment.getSnapshotTemplateName());
        item.put("snapshotTemplateDescription", assignment.getSnapshotTemplateDescription());
        item.put("snapshotTemplateCategory", assignment.getSnapshotTemplateCategory());
        item.put("snapshotDifficultyName", assignment.getSnapshotDifficultyName());
        item.put("snapshotDifficultyReward", assignment.getSnapshotDifficultyReward());
        item.put("createdAt", assignment.getCreatedAt());

        // Load attempts
        List<TaskAttempt> attempts = taskAttemptMapper.findByAssignmentId(assignment.getId());
        List<Map<String, Object>> attemptData = new ArrayList<>();
        for (TaskAttempt attempt : attempts) {
            Map<String, Object> attemptItem = new LinkedHashMap<>();
            attemptItem.put("id", attempt.getId());
            attemptItem.put("attemptNumber", attempt.getAttemptNumber());
            attemptItem.put("content", attempt.getContent());
            attemptItem.put("attachments", attempt.getAttachments());
            attemptItem.put("submittedAt", attempt.getSubmittedAt());
            attemptItem.put("isLate", attempt.getIsLate());
            attemptItem.put("latePolicyApplied", attempt.getLatePolicyApplied());

            // Load review for this attempt
            Optional<TaskReview> review = taskReviewMapper.findByAttemptId(attempt.getId());
            review.ifPresent(r -> {
                attemptItem.put("reviewId", r.getId());
                attemptItem.put("decision", r.getDecision());
                attemptItem.put("reviewReason", r.getReason());
                attemptItem.put("reviewerId", r.getReviewerId());
                attemptItem.put("reviewedAt", r.getCreatedAt());
            });

            attemptData.add(attemptItem);
        }
        item.put("attempts", attemptData);

        return item;
    }

    // ========== Task 11.3: Type config helpers ==========

    /**
     * Parse a date field (start_date / end_date) from the type_config JSON.
     * Returns null if the field is missing or invalid.
     */
    private LocalDateTime parseLimitedDate(String typeConfig, String field) {
        if (typeConfig == null || typeConfig.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(typeConfig, Map.class);
            Object val = config.get(field);
            if (val == null) {
                return null;
            }
            return LocalDateTime.parse(val.toString());
        } catch (JsonProcessingException | java.time.format.DateTimeParseException e) {
            log.debug("Failed to parse {} from type_config: {}", field, e.getMessage());
            return null;
        }
    }

    /**
     * Parse max_submissions from the STANDING type_config JSON.
     * Returns null if not configured (unlimited submissions).
     */
    private Integer parseMaxSubmissions(String typeConfig) {
        if (typeConfig == null || typeConfig.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(typeConfig, Map.class);
            Object val = config.get("max_submissions");
            if (val == null) {
                return null;
            }
            return ((Number) val).intValue();
        } catch (JsonProcessingException | ClassCastException e) {
            log.debug("Failed to parse max_submissions from type_config: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildApprovalResult(TaskReview review, Long attemptId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewId", review.getId());
        result.put("attemptId", attemptId);
        result.put("decision", review.getDecision());
        result.put("reason", review.getReason());
        result.put("reviewedAt", review.getCreatedAt());
        return result;
    }

    private boolean isSameContent(String content, String attachments, TaskAttempt existing) {
        // Compare content
        if (content == null && existing.getContent() != null) return false;
        if (content != null && !content.equals(existing.getContent())) return false;
        // Compare attachments
        if (attachments == null && existing.getAttachments() != null) return false;
        if (attachments != null && !attachments.equals(existing.getAttachments())) return false;
        return true;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractAndValidateIdempotencyKey(Map<String, Object> request) {
        Object keyObj = request.get("idempotencyKey");
        if (keyObj == null) {
            return null;
        }
        String key = keyObj.toString().trim();
        if (key.isEmpty()) {
            return null;
        }
        if (key.length() > 128) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "idempotencyKey must not exceed 128 characters");
        }
        return key;
    }

    public Long extractLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        return ((Number) val).longValue();
    }

    public String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        return val.toString();
    }

    public Long getSingleFamilyId() {
        var families = familyMapper.selectList(null);
        if (families.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "No family found");
        }
        return families.get(0).getId();
    }

    public void verifyParentRole(List<String> roles) {
        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN);
        }
    }

    public void verifyChildRole(List<String> roles) {
        if (!roles.contains(AuthConstants.ROLE_CHILD)) {
            throw new BusinessException(ErrorCode.TASK_REVIEW_FORBIDDEN);
        }
    }
}
