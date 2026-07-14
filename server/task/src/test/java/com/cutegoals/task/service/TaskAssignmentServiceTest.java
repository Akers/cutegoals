package com.cutegoals.task.service;

import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.task.*;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.task.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskAssignmentServiceTest {

    @Mock private TaskTemplateMapper taskTemplateMapper;
    @Mock private TaskDifficultyMapper taskDifficultyMapper;
    @Mock private TaskRecurrenceRuleMapper taskRecurrenceRuleMapper;
    @Mock private TaskAssignmentMapper taskAssignmentMapper;
    @Mock private TaskAssignmentSnapshotMapper taskAssignmentSnapshotMapper;
    @Mock private TaskChildMapper taskChildMapper;
    @Mock private FamilyMapper familyMapper;
    @Mock private TaskTemplateService taskTemplateService;
    @Mock private AuditService auditService;

    private TaskAssignmentService taskAssignmentService;

    private final Long familyId = 1L;
    private final Long accountId = 100L;
    private final Long childId = 200L;
    private final Long templateId = 10L;
    private final Long difficultyId = 20L;

    @BeforeEach
    void setUp() {
        taskAssignmentService = new TaskAssignmentService(
                taskTemplateMapper, taskDifficultyMapper, taskRecurrenceRuleMapper,
                taskAssignmentMapper, taskAssignmentSnapshotMapper, taskChildMapper,
                familyMapper, taskTemplateService, auditService);
    }

    // ========== Task 3.5: Single Task Assignment ==========

    @Test
    void shouldCreateSingleAssignmentSuccessfully() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("deadline", LocalDateTime.now().plusDays(1).toString());
        request.put("idempotencyKey", "key-001");

        TaskTemplate template = createSampleTemplate();
        TaskDifficulty difficulty = createSampleDifficulty();
        ChildProfile child = createSampleChild();

        when(taskAssignmentMapper.findByIdempotencyKey("key-001", familyId)).thenReturn(Optional.empty());
        when(taskTemplateService.getActiveTemplate(templateId, familyId)).thenReturn(template);
        when(taskTemplateService.getEnabledDifficulty(difficultyId, templateId)).thenReturn(difficulty);
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));

        doAnswer(invocation -> {
            TaskAssignment a = invocation.getArgument(0);
            a.setId(1L);
            return 1;
        }).when(taskAssignmentMapper).insert(any(TaskAssignment.class));

        doReturn(1).when(taskAssignmentSnapshotMapper).insert(any(TaskAssignmentSnapshot.class));

        TaskAssignment result = taskAssignmentService.createAssignment(request, familyId, accountId);

        assertNotNull(result);
        verify(taskAssignmentSnapshotMapper).insert(any(TaskAssignmentSnapshot.class));
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldReturnExistingForIdempotentCreate() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("deadline", LocalDateTime.now().plusDays(1).toString());
        request.put("idempotencyKey", "key-001");

        TaskAssignment existing = createSampleAssignment();
        when(taskAssignmentMapper.findByIdempotencyKey("key-001", familyId)).thenReturn(Optional.of(existing));

        TaskAssignment result = taskAssignmentService.createAssignment(request, familyId, accountId);
        assertEquals(existing.getId(), result.getId());
        verify(taskAssignmentMapper, never()).insert(any(TaskAssignment.class));
    }

    @Test
    void shouldThrowIdempotencyConflictOnDifferentContent() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("deadline", LocalDateTime.now().plusDays(1).toString());
        request.put("idempotencyKey", "key-001");

        TaskAssignment existing = createSampleAssignment();
        // Change key fields to differ from request
        existing.setTemplateId(999L);
        existing.setChildId(888L);
        when(taskAssignmentMapper.findByIdempotencyKey("key-001", familyId)).thenReturn(Optional.of(existing));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.createAssignment(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_IDEMPOTENCY_CONFLICT, e.getErrorCode());
        verify(taskAssignmentMapper, never()).insert(any(TaskAssignment.class));
    }

    @Test
    void shouldFailCreateAssignmentWithPastDeadline() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("deadline", LocalDateTime.now().minusDays(1).toString());
        request.put("idempotencyKey", "key-002");

        TaskTemplate template = createSampleTemplate();
        TaskDifficulty difficulty = createSampleDifficulty();
        ChildProfile child = createSampleChild();

        when(taskAssignmentMapper.findByIdempotencyKey("key-002", familyId)).thenReturn(Optional.empty());
        when(taskTemplateService.getActiveTemplate(templateId, familyId)).thenReturn(template);
        when(taskTemplateService.getEnabledDifficulty(difficultyId, templateId)).thenReturn(difficulty);
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.createAssignment(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_INVALID_DEADLINE, e.getErrorCode());
    }

    @Test
    void shouldFailCreateAssignmentWithInactiveTemplate() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("deadline", LocalDateTime.now().plusDays(1).toString());

        when(taskTemplateService.getActiveTemplate(templateId, familyId))
                .thenThrow(new BusinessException(ErrorCode.TASK_ASSIGNMENT_TEMPLATE_INACTIVE));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.createAssignment(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_TEMPLATE_INACTIVE, e.getErrorCode());
    }

    @Test
    void shouldFailCreateAssignmentWithInactiveDifficulty() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("deadline", LocalDateTime.now().plusDays(1).toString());

        TaskTemplate template = createSampleTemplate();
        when(taskTemplateService.getActiveTemplate(templateId, familyId)).thenReturn(template);
        when(taskTemplateService.getEnabledDifficulty(difficultyId, templateId))
                .thenThrow(new BusinessException(ErrorCode.TASK_ASSIGNMENT_DIFFICULTY_INACTIVE));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.createAssignment(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_DIFFICULTY_INACTIVE, e.getErrorCode());
    }

    @Test
    void shouldFailCreateWithWrongFamilyChild() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("deadline", LocalDateTime.now().plusDays(1).toString());

        TaskTemplate template = createSampleTemplate();
        TaskDifficulty difficulty = createSampleDifficulty();
        ChildProfile child = createSampleChild();
        child.setFamilyId(999L);

        when(taskTemplateService.getActiveTemplate(templateId, familyId)).thenReturn(template);
        when(taskTemplateService.getEnabledDifficulty(difficultyId, templateId)).thenReturn(difficulty);
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.createAssignment(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_CHILD_NOT_FOUND, e.getErrorCode());
    }

    // ========== Task 3.6: Batch Assignment ==========

    @Test
    void shouldCreateBatchAssignmentsSuccessfully() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childIds", List.of(childId));
        request.put("startDate", LocalDate.now().toString());
        request.put("endDate", LocalDate.now().plusDays(2).toString());
        request.put("idempotencyKey", "batch-key-001");

        TaskTemplate template = createSampleTemplate();
        TaskDifficulty difficulty = createSampleDifficulty();
        ChildProfile child = createSampleChild();

        when(taskAssignmentMapper.findByIdempotencyKey("batch-key-001", familyId)).thenReturn(Optional.empty());
        when(taskTemplateService.getActiveTemplate(templateId, familyId)).thenReturn(template);
        when(taskTemplateService.getEnabledDifficulty(difficultyId, templateId)).thenReturn(difficulty);
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));
        doReturn(1).when(taskAssignmentMapper).insert(any(TaskAssignment.class));
        doReturn(1).when(taskAssignmentSnapshotMapper).insert(any(TaskAssignmentSnapshot.class));

        List<TaskAssignment> results = taskAssignmentService.createBatchAssignments(request, familyId, accountId);
        assertEquals(1, results.size());
        verify(auditService).record(eq(AuditEvent.ASSIGNMENT_BATCH_CREATED), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldRejectBatchWithInvalidDateRange() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childIds", List.of(childId));
        request.put("startDate", LocalDate.now().plusDays(5).toString());
        request.put("endDate", LocalDate.now().toString());

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.createBatchAssignments(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_INVALID_DATE_RANGE, e.getErrorCode());
    }

    @Test
    void shouldRejectBatchWithExcessiveDateRange() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childIds", List.of(childId));
        request.put("startDate", LocalDate.now().toString());
        request.put("endDate", LocalDate.now().plusDays(400).toString());

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.createBatchAssignments(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_INVALID_DATE_RANGE, e.getErrorCode());
    }

    // ========== Task 3.7: Recurring Generation ==========

    @Test
    void shouldGenerateRecurringAssignments() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("startDate", "2026-04-08");
        request.put("endDate", "2026-04-14");

        TaskTemplate template = createSampleTemplate();
        ChildProfile child = createSampleChild();
        TaskDifficulty difficulty = createSampleDifficulty();
        TaskRecurrenceRule rule = new TaskRecurrenceRule();
        rule.setTemplateId(templateId);
        rule.setRuleType("DAILY");

        when(taskTemplateService.getActiveTemplate(templateId, familyId)).thenReturn(template);
        when(taskRecurrenceRuleMapper.findByTemplateId(templateId)).thenReturn(Optional.of(rule));
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));
        when(taskTemplateService.getEnabledDifficulty(difficultyId, templateId)).thenReturn(difficulty);
        when(taskAssignmentMapper.countByOccurrenceKey(anyString())).thenReturn(0);
        doReturn(1).when(taskAssignmentMapper).insert(any(TaskAssignment.class));
        doReturn(1).when(taskAssignmentSnapshotMapper).insert(any(TaskAssignmentSnapshot.class));

        Map<String, Object> result = taskAssignmentService.generateRecurringAssignments(request, familyId, accountId);
        assertEquals(7, result.get("created"));
        assertEquals(0, result.get("skipped"));
    }

    @Test
    void shouldSkipExistingOccurrences() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("startDate", "2026-04-08");
        request.put("endDate", "2026-04-10");

        TaskTemplate template = createSampleTemplate();
        ChildProfile child = createSampleChild();
        TaskDifficulty difficulty = createSampleDifficulty();
        TaskRecurrenceRule rule = new TaskRecurrenceRule();
        rule.setTemplateId(templateId);
        rule.setRuleType("WEEKDAYS");

        when(taskTemplateService.getActiveTemplate(templateId, familyId)).thenReturn(template);
        when(taskRecurrenceRuleMapper.findByTemplateId(templateId)).thenReturn(Optional.of(rule));
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));
        when(taskTemplateService.getEnabledDifficulty(difficultyId, templateId)).thenReturn(difficulty);
        when(taskAssignmentMapper.countByOccurrenceKey(anyString())).thenReturn(1);

        Map<String, Object> result = taskAssignmentService.generateRecurringAssignments(request, familyId, accountId);
        assertEquals(0, result.get("created"));
        assertEquals(3, result.get("skipped"));
    }

    @Test
    void shouldFailGenerateWithoutRecurrenceRule() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("startDate", "2026-04-08");
        request.put("endDate", "2026-04-10");

        when(taskTemplateService.getActiveTemplate(templateId, familyId)).thenReturn(createSampleTemplate());
        when(taskRecurrenceRuleMapper.findByTemplateId(templateId)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.generateRecurringAssignments(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_RECURRENCE_NOT_CONFIGURED, e.getErrorCode());
    }

    // ========== Task 3.9: Cancel ==========

    @Test
    void shouldCancelPendingAssignment() {
        TaskAssignment assignment = createSampleAssignment();
        assignment.setStatus("PENDING");
        assignment.setCancelled(false);

        TaskAssignment cancelledAssignment = createSampleAssignment();
        cancelledAssignment.setCancelled(true);
        cancelledAssignment.setCancelledAt(LocalDateTime.now());
        cancelledAssignment.setCancelledBy(accountId);
        cancelledAssignment.setCancelledReason("No longer needed");

        when(taskAssignmentMapper.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));
        when(taskAssignmentMapper.cancelWithCondition(1L, accountId, "No longer needed")).thenReturn(1);
        when(taskAssignmentMapper.findById(1L)).thenReturn(Optional.of(cancelledAssignment));

        TaskAssignment result = taskAssignmentService.cancelAssignment(1L, "No longer needed", familyId, accountId);
        assertTrue(result.getCancelled());
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldRejectCancelApprovedAssignment() {
        TaskAssignment assignment = createSampleAssignment();
        assignment.setStatus("APPROVED");
        assignment.setCancelled(false);

        when(taskAssignmentMapper.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.cancelAssignment(1L, "reason", familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_ALREADY_APPROVED, e.getErrorCode());
    }

    @Test
    void shouldBeIdempotentOnCancel() {
        TaskAssignment assignment = createSampleAssignment();
        assignment.setCancelled(true);

        when(taskAssignmentMapper.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));

        TaskAssignment result = taskAssignmentService.cancelAssignment(1L, "reason", familyId, accountId);
        assertTrue(result.getCancelled());
        verify(taskAssignmentMapper, never()).cancelWithCondition(anyLong(), anyLong(), anyString());
    }

    // ========== Task 3.10: Update ==========

    @Test
    void shouldUpdatePendingAssignment() {
        TaskAssignment assignment = createSampleAssignment();
        assignment.setStatus("PENDING");
        assignment.setVersion(1);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("deadline", LocalDateTime.now().plusDays(3).toString());
        request.put("version", 1);

        when(taskAssignmentMapper.findById(1L)).thenReturn(Optional.of(assignment));
        when(taskAssignmentMapper.updateById(any(TaskAssignment.class))).thenReturn(1);
        when(taskAssignmentMapper.findById(1L)).thenReturn(Optional.of(assignment));

        TaskAssignment result = taskAssignmentService.updateAssignment(1L, request, familyId, accountId);
        assertNotNull(result);
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldRejectUpdateSubmittedAssignment() {
        TaskAssignment assignment = createSampleAssignment();
        assignment.setStatus("SUBMITTED");

        when(taskAssignmentMapper.findById(1L)).thenReturn(Optional.of(assignment));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.updateAssignment(1L, new LinkedHashMap<>(), familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_NOT_EDITABLE, e.getErrorCode());
    }

    @Test
    void shouldRejectUpdateWithVersionConflict() {
        TaskAssignment assignment = createSampleAssignment();
        assignment.setStatus("PENDING");
        assignment.setVersion(2);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("version", 1);

        when(taskAssignmentMapper.findById(1L)).thenReturn(Optional.of(assignment));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.updateAssignment(1L, request, familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_VERSION_CONFLICT, e.getErrorCode());
    }

    @Test
    void shouldRejectUpdateCancelledAssignment() {
        TaskAssignment assignment = createSampleAssignment();
        assignment.setCancelled(true);

        when(taskAssignmentMapper.findById(1L)).thenReturn(Optional.of(assignment));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskAssignmentService.updateAssignment(1L, new LinkedHashMap<>(), familyId, accountId));
        assertEquals(ErrorCode.TASK_ASSIGNMENT_NOT_EDITABLE, e.getErrorCode());
    }

    // ========== Snapshot Persistence (Task 3.7) ==========

    @Test
    void shouldPersistSnapshotOnCreate() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("deadline", LocalDateTime.now().plusDays(1).toString());

        TaskTemplate template = createSampleTemplate();
        template.setName("Snapshot Test");
        template.setTaskType("REPEAT");
        template.setTypeConfig("{\"interval\":\"daily\"}");
        TaskDifficulty difficulty = createSampleDifficulty();
        difficulty.setName("Expert");
        difficulty.setRewardPoints(50);
        ChildProfile child = createSampleChild();

        when(taskTemplateService.getActiveTemplate(templateId, familyId)).thenReturn(template);
        when(taskTemplateService.getEnabledDifficulty(difficultyId, templateId)).thenReturn(difficulty);
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));

        doAnswer(invocation -> {
            TaskAssignment a = invocation.getArgument(0);
            a.setId(1L);
            assertEquals("Snapshot Test", a.getSnapshotTemplateName());
            assertEquals("Expert", a.getSnapshotDifficultyName());
            assertEquals(Integer.valueOf(50), a.getSnapshotDifficultyReward());
            assertEquals("Study", a.getSnapshotTemplateCategory());
            assertEquals("REPEAT", a.getSnapshotTemplateTaskType());
            assertEquals("{\"interval\":\"daily\"}", a.getSnapshotTemplateTypeConfig());
            return 1;
        }).when(taskAssignmentMapper).insert(any(TaskAssignment.class));

        doAnswer(invocation -> {
            TaskAssignmentSnapshot s = invocation.getArgument(0);
            assertEquals("Snapshot Test", s.getTemplateName());
            assertEquals("Expert", s.getDifficultyName());
            assertEquals(Integer.valueOf(50), s.getDifficultyRewardPoints());
            return 1;
        }).when(taskAssignmentSnapshotMapper).insert(any(TaskAssignmentSnapshot.class));

        taskAssignmentService.createAssignment(request, familyId, accountId);
        verify(taskAssignmentSnapshotMapper).insert(any(TaskAssignmentSnapshot.class));
    }

    @Test
    void shouldSnapshotTaskTypeAndTypeConfigOnCreate() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("templateId", templateId);
        request.put("difficultyId", difficultyId);
        request.put("childId", childId);
        request.put("deadline", LocalDateTime.now().plusDays(1).toString());

        TaskTemplate template = createSampleTemplate();
        template.setTaskType("STANDING");
        template.setTypeConfig("{\"maxStandingMinutes\":30}");
        TaskDifficulty difficulty = createSampleDifficulty();
        ChildProfile child = createSampleChild();

        when(taskTemplateService.getActiveTemplate(templateId, familyId)).thenReturn(template);
        when(taskTemplateService.getEnabledDifficulty(difficultyId, templateId)).thenReturn(difficulty);
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));

        final TaskAssignment[] captured = new TaskAssignment[1];
        doAnswer(invocation -> {
            TaskAssignment a = invocation.getArgument(0);
            a.setId(1L);
            captured[0] = a;
            return 1;
        }).when(taskAssignmentMapper).insert(any(TaskAssignment.class));

        doReturn(1).when(taskAssignmentSnapshotMapper).insert(any(TaskAssignmentSnapshot.class));

        taskAssignmentService.createAssignment(request, familyId, accountId);

        assertNotNull(captured[0]);
        assertEquals("STANDING", captured[0].getSnapshotTemplateTaskType());
        assertEquals("{\"maxStandingMinutes\":30}", captured[0].getSnapshotTemplateTypeConfig());
    }

    @Test
    void shouldNotAffectHistoricalNullRecords() {
        // This verifies that existing assignments with NULL snapshot_template_task_type
        // and snapshot_template_type_config remain untouched (no migration needed).
        TaskAssignment historical = createSampleAssignment();
        historical.setSnapshotTemplateTaskType(null);
        historical.setSnapshotTemplateTypeConfig(null);

        when(taskAssignmentMapper.findById(1L)).thenReturn(Optional.of(historical));

        TaskAssignment result = taskAssignmentMapper.findById(1L).orElseThrow();
        assertNull(result.getSnapshotTemplateTaskType());
        assertNull(result.getSnapshotTemplateTypeConfig());
    }

    // ========== Helpers ==========

    private TaskTemplate createSampleTemplate() {
        TaskTemplate t = new TaskTemplate();
        t.setId(templateId);
        t.setFamilyId(familyId);
        t.setName("Test Template");
        t.setCategory("Study");
        t.setDescription("Do your best");
        t.setEnabled(true);
        t.setDeleted(false);
        t.setVersion(1);
        t.setTaskType("LIMITED");
        t.setTypeConfig("{\"maxTimes\":3}");
        return t;
    }

    private TaskDifficulty createSampleDifficulty() {
        TaskDifficulty d = new TaskDifficulty();
        d.setId(difficultyId);
        d.setTemplateId(templateId);
        d.setName("Normal");
        d.setDisplayOrder(1);
        d.setRewardPoints(10);
        d.setEnabled(true);
        return d;
    }

    private ChildProfile createSampleChild() {
        ChildProfile c = new ChildProfile();
        c.setId(childId);
        c.setFamilyId(familyId);
        c.setNickname("Test Kid");
        c.setStatus("ACTIVE");
        return c;
    }

    private TaskAssignment createSampleAssignment() {
        TaskAssignment a = new TaskAssignment();
        a.setId(1L);
        a.setFamilyId(familyId);
        a.setTemplateId(templateId);
        a.setChildId(childId);
        a.setDifficultyId(difficultyId);
        a.setDeadline(LocalDateTime.now().plusDays(1));
        a.setStatus("PENDING");
        a.setLatePolicy("ALLOW");
        a.setCancelled(false);
        a.setVersion(1);
        a.setSnapshotTemplateName("Test Template");
        a.setSnapshotDifficultyName("Normal");
        a.setSnapshotDifficultyReward(10);
        a.setSnapshotTemplateCategory("Study");
        return a;
    }
}
