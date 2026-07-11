package com.cutegoals.task.service;

import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.entity.task.TaskDifficulty;
import com.cutegoals.common.entity.task.TaskRecurrenceRule;
import com.cutegoals.common.entity.task.TaskTemplate;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.task.mapper.*;
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
class TaskTemplateServiceTest {

    @Mock private TaskTemplateMapper taskTemplateMapper;
    @Mock private TaskDifficultyMapper taskDifficultyMapper;
    @Mock private TaskRecurrenceRuleMapper taskRecurrenceRuleMapper;
    @Mock private FamilyMapper familyMapper;
    @Mock private TaskChildMapper taskChildMapper;
    @Mock private AuditService auditService;

    private TaskTemplateService taskTemplateService;

    private final Long familyId = 1L;
    private final Long accountId = 100L;

    @BeforeEach
    void setUp() {
        taskTemplateService = new TaskTemplateService(
                taskTemplateMapper, taskDifficultyMapper, taskRecurrenceRuleMapper,
                familyMapper, taskChildMapper, auditService);
    }

    // ========== Task 3.1: Create template ==========

    @Test
    void shouldCreateTemplateSuccessfully() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Math Homework");
        request.put("category", "Study");
        request.put("description", "Complete math exercises");
        request.put("icon", "/icons/math.png");

        List<Map<String, Object>> difficulties = new ArrayList<>();
        Map<String, Object> diff1 = new LinkedHashMap<>();
        diff1.put("name", "Easy");
        diff1.put("displayOrder", 1);
        diff1.put("rewardPoints", 10);
        difficulties.add(diff1);
        Map<String, Object> diff2 = new LinkedHashMap<>();
        diff2.put("name", "Hard");
        diff2.put("displayOrder", 2);
        diff2.put("rewardPoints", 30);
        difficulties.add(diff2);
        request.put("difficulties", difficulties);

        doAnswer(invocation -> {
            TaskTemplate t = invocation.getArgument(0);
            t.setId(1L);
            return null;
        }).when(taskTemplateMapper).insert(any(TaskTemplate.class));
        when(taskTemplateMapper.findById(1L)).thenReturn(Optional.of(createSampleTemplate()));

        TaskTemplate result = taskTemplateService.createTemplate(request, familyId, accountId);

        assertNotNull(result);
        verify(taskDifficultyMapper, times(2)).insert(any(TaskDifficulty.class));
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldFailCreateTemplateWithBlankName() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "  ");
        request.put("category", "Study");
        request.put("difficulties", createSampleDifficulties());

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.createTemplate(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldFailCreateTemplateWithOverlyLongName() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "a".repeat(101));
        request.put("category", "Study");
        request.put("difficulties", createSampleDifficulties());

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.createTemplate(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldFailCreateTemplateWithOverlyLongDescription() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Valid Name");
        request.put("category", "Study");
        request.put("description", "a".repeat(2001));
        request.put("difficulties", createSampleDifficulties());

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.createTemplate(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldFailCreateTemplateWithEmptyDifficulties() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Valid Name");
        request.put("category", "Study");
        request.put("difficulties", new ArrayList<>());

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.createTemplate(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldFailCreateTemplateWithDuplicateDisplayOrder() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Valid Name");
        request.put("category", "Study");

        List<Map<String, Object>> difficulties = new ArrayList<>();
        Map<String, Object> diff1 = new LinkedHashMap<>();
        diff1.put("name", "Easy");
        diff1.put("displayOrder", 1);
        diff1.put("rewardPoints", 10);
        difficulties.add(diff1);
        Map<String, Object> diff2 = new LinkedHashMap<>();
        diff2.put("name", "Hard");
        diff2.put("displayOrder", 1);
        difficulties.add(diff2);
        request.put("difficulties", difficulties);

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.createTemplate(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED, e.getErrorCode());
    }

    // ========== Task 3.2: Recurrence Rule ==========

    @Test
    void shouldCreateTemplateWithCustomWeekdaysRecurrence() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Weekly Task");
        request.put("category", "Chores");

        List<Map<String, Object>> difficulties = createSampleDifficulties();
        request.put("difficulties", difficulties);

        Map<String, Object> ruleReq = new LinkedHashMap<>();
        ruleReq.put("ruleType", "CUSTOM_WEEKDAYS");
        ruleReq.put("customWeekdays", List.of(1, 3, 5));
        request.put("recurrenceRule", ruleReq);

        doAnswer(invocation -> {
            TaskTemplate t = invocation.getArgument(0);
            t.setId(1L);
            return null;
        }).when(taskTemplateMapper).insert(any(TaskTemplate.class));
        when(taskTemplateMapper.findById(1L)).thenReturn(Optional.of(createSampleTemplate()));
        doAnswer(invocation -> {
            TaskRecurrenceRule r = invocation.getArgument(0);
            r.setId(1L);
            return 1;
        }).when(taskRecurrenceRuleMapper).insert(any(TaskRecurrenceRule.class));

        TaskTemplate result = taskTemplateService.createTemplate(request, familyId, accountId);
        assertNotNull(result);
    }

    @Test
    void shouldFailCreateTemplateWithEmptyCustomWeekdays() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Task");
        request.put("category", "Test");
        request.put("difficulties", createSampleDifficulties());
        Map<String, Object> ruleReq = new LinkedHashMap<>();
        ruleReq.put("ruleType", "CUSTOM_WEEKDAYS");
        ruleReq.put("customWeekdays", new ArrayList<>());
        request.put("recurrenceRule", ruleReq);

        doAnswer(invocation -> {
            TaskTemplate t = invocation.getArgument(0);
            t.setId(1L);
            return null;
        }).when(taskTemplateMapper).insert(any(TaskTemplate.class));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.createTemplate(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_INVALID_RECURRENCE, e.getErrorCode());
    }

    @Test
    void shouldFailCreateTemplateWithInvalidWeekday() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Task");
        request.put("category", "Test");
        request.put("difficulties", createSampleDifficulties());
        Map<String, Object> ruleReq = new LinkedHashMap<>();
        ruleReq.put("ruleType", "CUSTOM_WEEKDAYS");
        ruleReq.put("customWeekdays", List.of(0, 8));
        request.put("recurrenceRule", ruleReq);

        doAnswer(invocation -> {
            TaskTemplate t = invocation.getArgument(0);
            t.setId(1L);
            return null;
        }).when(taskTemplateMapper).insert(any(TaskTemplate.class));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.createTemplate(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_INVALID_RECURRENCE, e.getErrorCode());
    }

    @Test
    void shouldCreateTemplateWithoutRecurrence() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "One-off Task");
        request.put("category", "Test");
        request.put("difficulties", createSampleDifficulties());

        doAnswer(invocation -> {
            TaskTemplate t = invocation.getArgument(0);
            t.setId(1L);
            return null;
        }).when(taskTemplateMapper).insert(any(TaskTemplate.class));
        when(taskTemplateMapper.findById(1L)).thenReturn(Optional.of(createSampleTemplate()));

        TaskTemplate result = taskTemplateService.createTemplate(request, familyId, accountId);
        assertNotNull(result);
        verify(taskRecurrenceRuleMapper, never()).insert(any(TaskRecurrenceRule.class));
    }

    // ========== Task 3.3: Difficulty Management ==========

    @Test
    void shouldFailWithNonPositiveRewardPoints() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Task");
        request.put("category", "Test");

        List<Map<String, Object>> difficulties = new ArrayList<>();
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("name", "Easy");
        diff.put("displayOrder", 1);
        diff.put("rewardPoints", 0);
        difficulties.add(diff);
        request.put("difficulties", difficulties);

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.createTemplate(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldFailWithDecimalRewardPoints() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Task");
        request.put("category", "Test");

        List<Map<String, Object>> difficulties = new ArrayList<>();
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("name", "Easy");
        diff.put("displayOrder", 1);
        diff.put("rewardPoints", 1.5);
        difficulties.add(diff);
        request.put("difficulties", difficulties);

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.createTemplate(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldFailWhenDisablingLastEnabledDifficulty() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Task");
        request.put("category", "Test");

        List<Map<String, Object>> difficulties = new ArrayList<>();
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("name", "Only");
        diff.put("displayOrder", 1);
        diff.put("rewardPoints", 10);
        diff.put("enabled", false);
        difficulties.add(diff);
        request.put("difficulties", difficulties);

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.createTemplate(request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_REQUIRES_ACTIVE_DIFFICULTY, e.getErrorCode());
    }

    // ========== Task 3.4: Soft Delete ==========

    @Test
    void shouldSoftDeleteTemplate() {
        TaskTemplate template = createSampleTemplate();
        when(taskTemplateMapper.findById(1L)).thenReturn(Optional.of(template));
        when(taskTemplateMapper.updateById(any(TaskTemplate.class))).thenReturn(1);

        taskTemplateService.deleteTemplate(1L, familyId, accountId);

        assertTrue(template.getDeleted());
        assertNotNull(template.getDeletedAt());
        assertEquals(accountId, template.getDeletedBy());
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldBeIdempotentOnRepeatedDelete() {
        TaskTemplate template = createSampleTemplate();
        template.setDeleted(true);
        when(taskTemplateMapper.findById(1L)).thenReturn(Optional.of(template));

        taskTemplateService.deleteTemplate(1L, familyId, accountId);
        verify(taskTemplateMapper, never()).updateById(any(TaskTemplate.class));
    }

    @Test
    void shouldThrowNotFoundForWrongFamilyTemplateDelete() {
        TaskTemplate template = createSampleTemplate();
        template.setFamilyId(999L);
        when(taskTemplateMapper.findById(1L)).thenReturn(Optional.of(template));

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.deleteTemplate(1L, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_NOT_FOUND, e.getErrorCode());
    }

    // ========== Version Conflict (Task 3.4) ==========

    @Test
    void shouldThrowVersionConflictOnStaleUpdate() {
        TaskTemplate template = createSampleTemplate();
        template.setVersion(2);
        when(taskTemplateMapper.findById(1L)).thenReturn(Optional.of(template));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "New Name");
        request.put("version", 1);

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.updateTemplate(1L, request, familyId, accountId));
        assertEquals(ErrorCode.TASK_TEMPLATE_VERSION_CONFLICT, e.getErrorCode());
    }

    // ========== Role check ==========

    @Test
    void shouldAllowParentRole() {
        List<String> parentRoles = List.of("PARENT");
        assertDoesNotThrow(() -> taskTemplateService.verifyParentRole(parentRoles));
    }

    @Test
    void shouldAllowAdminRole() {
        List<String> adminRoles = List.of("INSTANCE_ADMIN");
        assertDoesNotThrow(() -> taskTemplateService.verifyParentRole(adminRoles));
    }

    @Test
    void shouldForbidChildRole() {
        List<String> childRoles = List.of("CHILD");
        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.verifyParentRole(childRoles));
        assertEquals(ErrorCode.TASK_TEMPLATE_FORBIDDEN, e.getErrorCode());
    }

    // ========== Query (Task 3.1) ==========

    @Test
    void shouldRejectInvalidPageSize() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pageSize", 101);

        BusinessException e = assertThrows(BusinessException.class,
                () -> taskTemplateService.queryTemplates(params, familyId));
        assertEquals(ErrorCode.TASK_TEMPLATE_INVALID_QUERY, e.getErrorCode());
    }

    // ========== Helpers ==========

    private TaskTemplate createSampleTemplate() {
        TaskTemplate t = new TaskTemplate();
        t.setId(1L);
        t.setFamilyId(familyId);
        t.setName("Test Template");
        t.setCategory("Test");
        t.setDescription("Description");
        t.setIcon("/icon.png");
        t.setEnabled(true);
        t.setDeleted(false);
        t.setVersion(1);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        return t;
    }

    private List<Map<String, Object>> createSampleDifficulties() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("name", "Easy");
        diff.put("displayOrder", 1);
        diff.put("rewardPoints", 10);
        list.add(diff);
        return list;
    }
}
