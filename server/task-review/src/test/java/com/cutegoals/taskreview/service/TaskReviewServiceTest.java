package com.cutegoals.taskreview.service;

import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.entity.points.PointsBalance;
import com.cutegoals.common.entity.points.PointsLedger;
import com.cutegoals.common.entity.task.TaskAssignment;
import com.cutegoals.common.entity.task.TaskAttempt;
import com.cutegoals.common.entity.task.TaskReview;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.points.mapper.PointsBalanceMapper;
import com.cutegoals.points.mapper.PointsLedgerMapper;
import com.cutegoals.task.mapper.TaskAssignmentMapper;
import com.cutegoals.task.mapper.TaskChildMapper;
import com.cutegoals.taskreview.mapper.TaskAttemptMapper;
import com.cutegoals.taskreview.mapper.TaskReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskReviewServiceTest {

    @Mock private TaskAttemptMapper taskAttemptMapper;
    @Mock private TaskReviewMapper taskReviewMapper;
    @Mock private TaskAssignmentMapper taskAssignmentMapper;
    @Mock private TaskChildMapper taskChildMapper;
    @Mock private FamilyMapper familyMapper;
    @Mock private PointsLedgerMapper pointsLedgerMapper;
    @Mock private PointsBalanceMapper pointsBalanceMapper;
    @Mock private AuditService auditService;

    private TaskReviewService taskReviewService;

    private final Long familyId = 1L;
    private final Long childId = 10L;
    private final Long accountId = 100L;
    private final Long assignmentId = 50L;
    private final Long attemptId = 200L;

    @BeforeEach
    void setUp() {
        taskReviewService = new TaskReviewService(
                taskAttemptMapper, taskReviewMapper, taskAssignmentMapper,
                taskChildMapper, familyMapper, pointsLedgerMapper,
                pointsBalanceMapper, auditService);
    }

    // ========== Task 4.1: Submission ==========

    @Test
    void shouldSubmitTaskSuccessfully() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Task is done!");
        request.put("idempotencyKey", "idem-001");

        TaskAssignment assignment = createSampleAssignment("PENDING", false);
        when(taskAssignmentMapper.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(assignment));

        when(taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId)).thenReturn(0);
        when(taskAttemptMapper.findByIdempotencyKey(childId, "idem-001")).thenReturn(Optional.empty());

        doAnswer(invocation -> {
            TaskAttempt a = invocation.getArgument(0);
            a.setId(attemptId);
            return 1;
        }).when(taskAttemptMapper).insert(any(TaskAttempt.class));

        TaskAttempt result = taskReviewService.submitTask(request, childId, familyId, accountId);

        assertNotNull(result);
        assertEquals(1, result.getAttemptNumber());
        assertFalse(result.getIsLate());
        verify(taskAssignmentMapper).updateById(any(TaskAssignment.class));
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldThrowForbiddenWhenSubmittingOtherChildsTask() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Task is done!");

        TaskAssignment assignment = createSampleAssignment("PENDING", false);
        assignment.setChildId(999L); // Different child
        when(taskAssignmentMapper.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(BusinessException.class, () ->
                taskReviewService.submitTask(request, childId, familyId, accountId));
    }

    @Test
    void shouldThrowForbiddenWhenForeignFamily() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Task is done!");

        TaskAssignment assignment = createSampleAssignment("PENDING", false);
        assignment.setFamilyId(999L);
        when(taskAssignmentMapper.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(BusinessException.class, () ->
                taskReviewService.submitTask(request, childId, familyId, accountId));
    }

    @Test
    void shouldThrowInvalidStateWhenAlreadySubmitted() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Task is done!");

        TaskAssignment assignment = createSampleAssignment("SUBMITTED", false);
        when(taskAssignmentMapper.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(assignment));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskReviewService.submitTask(request, childId, familyId, accountId));
        assertEquals(ErrorCode.TASK_REVIEW_INVALID_STATE, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenContentExceeds2000Chars() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "X".repeat(2001));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskReviewService.submitTask(request, childId, familyId, accountId));
        assertEquals(ErrorCode.TASK_SUBMISSION_VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenCancelled() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Task is done!");

        TaskAssignment assignment = createSampleAssignment("PENDING", true);
        when(taskAssignmentMapper.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(assignment));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskReviewService.submitTask(request, childId, familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_CANCELLED, ex.getErrorCode());
    }

    // ========== Task 4.2: Idempotency ==========

    @Test
    void shouldReturnExistingAttemptForIdempotentSubmit() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Done!");
        request.put("idempotencyKey", "idem-001");

        TaskAttempt existing = new TaskAttempt();
        existing.setId(attemptId);
        existing.setAssignmentId(assignmentId);
        existing.setChildId(childId);
        existing.setAttemptNumber(1);
        existing.setContent("Done!");
        existing.setIsLate(false);

        when(taskAttemptMapper.findByIdempotencyKey(childId, "idem-001")).thenReturn(Optional.of(existing));

        TaskAttempt result = taskReviewService.submitTask(request, childId, familyId, accountId);

        assertEquals(attemptId, result.getId());
        verify(taskAssignmentMapper, never()).findByIdForUpdate(any());
    }

    @Test
    void shouldThrowIdempotencyConflictOnDifferentContent() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Different content!");
        request.put("idempotencyKey", "idem-001");

        TaskAttempt existing = new TaskAttempt();
        existing.setId(attemptId);
        existing.setContent("Original content");

        when(taskAttemptMapper.findByIdempotencyKey(childId, "idem-001")).thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskReviewService.submitTask(request, childId, familyId, accountId));
        assertEquals(ErrorCode.TASK_SUBMISSION_IDEMPOTENCY_CONFLICT, ex.getErrorCode());
    }

    // ========== Task 4.3: Late Submission ==========

    @Test
    void shouldRejectLateSubmissionWhenPolicyIsReject() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Late submission");

        TaskAssignment assignment = createSampleAssignment("PENDING", false);
        assignment.setDeadline(LocalDateTime.now().minusHours(1));
        assignment.setLatePolicy("REJECT");
        when(taskAssignmentMapper.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(assignment));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskReviewService.submitTask(request, childId, familyId, accountId));
        assertEquals(ErrorCode.TASK_SUBMISSION_LATE_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void shouldAcceptLateSubmissionWhenPolicyIsAllow() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Late but allowed");
        request.put("idempotencyKey", "idem-002");

        TaskAssignment assignment = createSampleAssignment("PENDING", false);
        assignment.setDeadline(LocalDateTime.now().minusHours(1));
        assignment.setLatePolicy("ALLOW");
        when(taskAssignmentMapper.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(assignment));

        when(taskAttemptMapper.findByIdempotencyKey(childId, "idem-002")).thenReturn(Optional.empty());
        when(taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId)).thenReturn(0);

        doAnswer(invocation -> {
            TaskAttempt a = invocation.getArgument(0);
            a.setId(attemptId);
            return 1;
        }).when(taskAttemptMapper).insert(any(TaskAttempt.class));

        TaskAttempt result = taskReviewService.submitTask(request, childId, familyId, accountId);

        assertNotNull(result);
        assertTrue(result.getIsLate());
        assertEquals("ALLOW", result.getLatePolicyApplied());
    }

    @Test
    void shouldTreatDeadlineEdgeAsOnTime() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Right on deadline!");
        request.put("idempotencyKey", "idem-003");

        // Set deadline to future, submission should be treated as on-time
        TaskAssignment assignment = createSampleAssignment("PENDING", false);
        assignment.setDeadline(LocalDateTime.now().plusMinutes(5)); // deadline in the future
        assignment.setLatePolicy("REJECT");
        when(taskAssignmentMapper.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(assignment));

        when(taskAttemptMapper.findByIdempotencyKey(childId, "idem-003")).thenReturn(Optional.empty());
        when(taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId)).thenReturn(0);

        doAnswer(invocation -> {
            TaskAttempt a = invocation.getArgument(0);
            a.setId(attemptId);
            return 1;
        }).when(taskAttemptMapper).insert(any(TaskAttempt.class));

        TaskAttempt result = taskReviewService.submitTask(request, childId, familyId, accountId);

        assertNotNull(result);
        assertFalse(result.getIsLate()); // Not late since submitted_at equals deadline
    }

    // ========== Task 4.4: Rejection ==========

    @Test
    void shouldRejectAttemptWithReason() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reason", "Need more details");

        TaskAttempt attempt = createSampleAttempt(1);
        when(taskAttemptMapper.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));

        TaskAssignment assignment = createSampleAssignment("SUBMITTED", false);
        when(taskAssignmentMapper.findById(assignmentId)).thenReturn(Optional.of(assignment));

        when(taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId)).thenReturn(1);
        when(taskReviewMapper.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.empty());

        doAnswer(invocation -> {
            TaskReview review = invocation.getArgument(0);
            review.setId(300L);
            return 1;
        }).when(taskReviewMapper).insert(any(TaskReview.class));

        TaskReview result = taskReviewService.rejectAttempt(attemptId, request, familyId, accountId);

        assertNotNull(result);
        assertEquals("REJECTED", result.getDecision());
        assertEquals("Need more details", result.getReason());
        verify(taskAssignmentMapper).updateById(any(TaskAssignment.class));
        verify(auditService).record(eq(AuditEvent.TASK_REVIEW_REJECTED), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldThrowWhenRejectWithoutReason() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reason", "   ");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskReviewService.rejectAttempt(attemptId, request, familyId, accountId));
        assertEquals(ErrorCode.TASK_REVIEW_REASON_REQUIRED, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenReasonExceeds1000Chars() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reason", "X".repeat(1001));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskReviewService.rejectAttempt(attemptId, request, familyId, accountId));
        assertEquals(ErrorCode.TASK_REVIEW_VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void shouldThrowAlreadyDecidedWhenRejectingReviewedAttempt() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reason", "Need more details");

        TaskAttempt attempt = createSampleAttempt(1);
        when(taskAttemptMapper.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));

        TaskAssignment assignment = createSampleAssignment("SUBMITTED", false);
        when(taskAssignmentMapper.findById(assignmentId)).thenReturn(Optional.of(assignment));

        when(taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId)).thenReturn(1);
        when(taskReviewMapper.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.of(createSampleReview("APPROVED")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskReviewService.rejectAttempt(attemptId, request, familyId, accountId));
        assertEquals(ErrorCode.TASK_REVIEW_ALREADY_DECIDED, ex.getErrorCode());
    }

    // ========== Task 4.5: Resubmission After Rejection ==========

    @Test
    void shouldCreateNextAttemptAfterRejection() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Resubmitted with fixes");
        request.put("idempotencyKey", "idem-004");

        TaskAssignment assignment = createSampleAssignment("REJECTED", false);
        when(taskAssignmentMapper.findByIdForUpdate(assignmentId)).thenReturn(Optional.of(assignment));

        when(taskAttemptMapper.findByIdempotencyKey(childId, "idem-004")).thenReturn(Optional.empty());
        when(taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId)).thenReturn(1); // One previous attempt

        doAnswer(invocation -> {
            TaskAttempt a = invocation.getArgument(0);
            a.setId(300L);
            return 1;
        }).when(taskAttemptMapper).insert(any(TaskAttempt.class));

        TaskAttempt result = taskReviewService.submitTask(request, childId, familyId, accountId);

        assertNotNull(result);
        assertEquals(2, result.getAttemptNumber()); // Next sequence number
        verify(taskAssignmentMapper).updateById(any(TaskAssignment.class));
    }

    // ========== Task 4.6: Approval with Points ==========

    @Test
    void shouldApproveAndCreateEarnTransaction() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reason", "Great job!");
        request.put("idempotencyKey", "approve-001");

        TaskAttempt attempt = createSampleAttempt(1);
        when(taskAttemptMapper.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));

        TaskAssignment assignment = createSampleAssignment("SUBMITTED", false);
        assignment.setSnapshotDifficultyReward(5);
        when(taskAssignmentMapper.findById(assignmentId)).thenReturn(Optional.of(assignment));

        when(taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId)).thenReturn(1);
        when(taskReviewMapper.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.empty());
        when(taskReviewMapper.findByIdempotencyKey("approve-001")).thenReturn(Optional.empty());
        when(pointsLedgerMapper.findByBusinessRef(childId, "ATTEMPT_" + attemptId)).thenReturn(Optional.empty());

        PointsBalance balance = new PointsBalance();
        balance.setChildId(childId);
        balance.setBalance(10);
        balance.setTotalEarned(10);
        balance.setVersion(1);
        when(pointsBalanceMapper.findByChildIdForUpdate(childId)).thenReturn(Optional.of(balance));

        when(pointsBalanceMapper.updateBalanceWithVersion(eq(childId), eq(15), eq(15), eq(1))).thenReturn(1);

        doAnswer(invocation -> {
            TaskReview r = invocation.getArgument(0);
            r.setId(400L);
            return 1;
        }).when(taskReviewMapper).insert(any(TaskReview.class));

        doAnswer(invocation -> {
            PointsLedger l = invocation.getArgument(0);
            l.setId(500L);
            return 1;
        }).when(pointsLedgerMapper).insert(any(PointsLedger.class));

        Map<String, Object> result = taskReviewService.approveAttempt(attemptId, request, familyId, accountId);

        assertNotNull(result);
        assertEquals("APPROVED", result.get("decision"));
        verify(pointsLedgerMapper).insert(any(PointsLedger.class));
        verify(pointsBalanceMapper).updateBalanceWithVersion(eq(childId), eq(15), eq(15), eq(1));
        verify(auditService, times(2)).record(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void shouldApproveWithZeroRewardPoints() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("idempotencyKey", "approve-002");

        TaskAttempt attempt = createSampleAttempt(1);
        when(taskAttemptMapper.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));

        TaskAssignment assignment = createSampleAssignment("SUBMITTED", false);
        assignment.setSnapshotDifficultyReward(0); // Zero reward
        when(taskAssignmentMapper.findById(assignmentId)).thenReturn(Optional.of(assignment));

        when(taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId)).thenReturn(1);
        when(taskReviewMapper.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.empty());
        when(taskReviewMapper.findByIdempotencyKey("approve-002")).thenReturn(Optional.empty());

        doAnswer(invocation -> {
            TaskReview r = invocation.getArgument(0);
            r.setId(400L);
            return 1;
        }).when(taskReviewMapper).insert(any(TaskReview.class));

        Map<String, Object> result = taskReviewService.approveAttempt(attemptId, request, familyId, accountId);

        assertNotNull(result);
        assertEquals("APPROVED", result.get("decision"));
        verify(pointsLedgerMapper, never()).insert(any(PointsLedger.class)); // No points for zero reward
    }

    @Test
    void shouldThrowAlreadyDecidedOnConcurrentApproval() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("idempotencyKey", "approve-003");

        TaskAttempt attempt = createSampleAttempt(1);
        when(taskAttemptMapper.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));

        TaskAssignment assignment = createSampleAssignment("SUBMITTED", false);
        when(taskAssignmentMapper.findById(assignmentId)).thenReturn(Optional.of(assignment));

        when(taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId)).thenReturn(1);
        when(taskReviewMapper.findByAttemptIdForUpdate(attemptId)).thenReturn(Optional.of(createSampleReview("APPROVED")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskReviewService.approveAttempt(attemptId, request, familyId, accountId));
        assertEquals(ErrorCode.TASK_REVIEW_ALREADY_DECIDED, ex.getErrorCode());
    }

    @Test
    void shouldThrowStaleAttemptWhenNotCurrent() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("idempotencyKey", "approve-004");

        TaskAttempt attempt = createSampleAttempt(1); // attempt_number = 1
        when(taskAttemptMapper.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));

        TaskAssignment assignment = createSampleAssignment("SUBMITTED", false);
        when(taskAssignmentMapper.findById(assignmentId)).thenReturn(Optional.of(assignment));

        when(taskAttemptMapper.getMaxAttemptNumber(assignmentId, childId)).thenReturn(2); // max is 2, attempt is 1

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskReviewService.approveAttempt(attemptId, request, familyId, accountId));
        assertEquals(ErrorCode.TASK_REVIEW_STALE_ATTEMPT, ex.getErrorCode());
    }

    // ========== Helpers ==========

    private TaskAssignment createSampleAssignment(String status, boolean cancelled) {
        TaskAssignment a = new TaskAssignment();
        a.setId(assignmentId);
        a.setFamilyId(familyId);
        a.setChildId(childId);
        a.setTemplateId(10L);
        a.setDifficultyId(20L);
        a.setDeadline(LocalDateTime.now().plusDays(1));
        a.setStatus(status);
        a.setLatePolicy("ALLOW");
        a.setCancelled(cancelled);
        a.setSnapshotTemplateName("Test Task");
        a.setSnapshotDifficultyName("Easy");
        a.setSnapshotDifficultyReward(5);
        a.setVersion(1);
        return a;
    }

    private TaskAttempt createSampleAttempt(int attemptNumber) {
        TaskAttempt a = new TaskAttempt();
        a.setId(attemptId);
        a.setAssignmentId(assignmentId);
        a.setChildId(childId);
        a.setAttemptNumber(attemptNumber);
        a.setContent("Task done!");
        a.setSubmittedAt(LocalDateTime.now());
        a.setIsLate(false);
        a.setLatePolicyApplied("ALLOW");
        return a;
    }

    private TaskReview createSampleReview(String decision) {
        TaskReview r = new TaskReview();
        r.setId(300L);
        r.setAttemptId(attemptId);
        r.setAssignmentId(assignmentId);
        r.setReviewerId(accountId);
        r.setDecision(decision);
        r.setReason("Test reason");
        return r;
    }
}
