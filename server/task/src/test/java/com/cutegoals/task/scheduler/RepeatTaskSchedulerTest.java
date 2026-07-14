package com.cutegoals.task.scheduler;

import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.task.TaskAssignment;
import com.cutegoals.common.entity.task.TaskTemplate;
import com.cutegoals.task.mapper.TaskAssignmentMapper;
import com.cutegoals.task.mapper.TaskTemplateMapper;
import com.cutegoals.task.service.TaskTemplateFrequencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepeatTaskSchedulerTest {

    @Mock private TaskTemplateMapper taskTemplateMapper;
    @Mock private TaskAssignmentMapper taskAssignmentMapper;
    @Mock private TaskTemplateFrequencyService taskTemplateFrequencyService;
    @Mock private AuditService auditService;

    private RepeatTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RepeatTaskScheduler(
                taskTemplateMapper, taskAssignmentMapper,
                taskTemplateFrequencyService, auditService);
    }

    @Test
    void shouldSkipWhenNoRepeatTemplates() {
        when(taskTemplateMapper.findEnabledRepeatTemplates()).thenReturn(List.of());

        scheduler.processRepeatAssignments();

        verify(taskAssignmentMapper, never()).findExpiredOpenByTemplate(anyLong());
        verify(taskAssignmentMapper, never()).findPendingOpenByTemplate(anyLong());
        verify(auditService).record(eq(AuditEvent.REPEAT_SCHEDULER_RUN), isNull(),
                eq("SUCCESS"), anyString());
    }

    @Test
    void shouldExpireOpenAssignmentsPastDeadline() {
        TaskTemplate template = createRepeatTemplate(1L);
        TaskAssignment expired = createAssignment(100L, "OPEN",
                LocalDateTime.now().minusDays(1));
        when(taskTemplateMapper.findEnabledRepeatTemplates()).thenReturn(List.of(template));
        when(taskAssignmentMapper.findExpiredOpenByTemplate(1L)).thenReturn(List.of(expired));
        when(taskAssignmentMapper.findPendingOpenByTemplate(1L)).thenReturn(List.of());
        when(taskAssignmentMapper.expireAssignment(100L)).thenReturn(1);

        scheduler.processRepeatAssignments();

        verify(taskAssignmentMapper).expireAssignment(100L);
        verify(auditService).record(eq(AuditEvent.REPEAT_SCHEDULER_RUN), isNull(),
                eq("SUCCESS"), contains("expired=1"));
    }

    @Test
    void shouldOpenPendingOpenAssignmentsOnTriggerDate() {
        TaskTemplate template = createRepeatTemplate(1L);
        TaskAssignment toOpen = createAssignment(100L, "PENDING_OPEN",
                LocalDateTime.now().minusHours(1));
        when(taskTemplateMapper.findEnabledRepeatTemplates()).thenReturn(List.of(template));
        when(taskAssignmentMapper.findExpiredOpenByTemplate(1L)).thenReturn(List.of());
        when(taskAssignmentMapper.findPendingOpenByTemplate(1L)).thenReturn(List.of(toOpen));
        when(taskAssignmentMapper.openAssignment(100L)).thenReturn(1);

        scheduler.processRepeatAssignments();

        verify(taskAssignmentMapper).openAssignment(100L);
        verify(auditService).record(eq(AuditEvent.REPEAT_SCHEDULER_RUN), isNull(),
                eq("SUCCESS"), contains("opened=1"));
    }

    @Test
    void shouldHandleTemplateErrorGracefully() {
        TaskTemplate template = createRepeatTemplate(1L);
        when(taskTemplateMapper.findEnabledRepeatTemplates()).thenReturn(List.of(template));
        when(taskAssignmentMapper.findExpiredOpenByTemplate(1L)).thenThrow(new RuntimeException("DB error"));

        // Should not throw
        scheduler.processRepeatAssignments();

        verify(auditService).record(eq(AuditEvent.REPEAT_SCHEDULER_RUN), isNull(),
                eq("SUCCESS"), anyString());
    }

    @Test
    void shouldBeIdempotentOnSameDay() {
        TaskTemplate template = createRepeatTemplate(1L);
        // Already expired/opened, so expireAssignment and openAssignment return 0
        when(taskTemplateMapper.findEnabledRepeatTemplates()).thenReturn(List.of(template));
        when(taskAssignmentMapper.findExpiredOpenByTemplate(1L)).thenReturn(List.of());
        when(taskAssignmentMapper.findPendingOpenByTemplate(1L)).thenReturn(List.of());

        // Run twice
        scheduler.processRepeatAssignments();
        scheduler.processRepeatAssignments();

        verify(auditService, times(2)).record(eq(AuditEvent.REPEAT_SCHEDULER_RUN),
                isNull(), eq("SUCCESS"), anyString());
    }

    // ========== Helpers ==========

    private TaskTemplate createRepeatTemplate(Long id) {
        TaskTemplate t = new TaskTemplate();
        t.setId(id);
        t.setTaskType("REPEAT");
        t.setTypeConfig("{\"frequency\":\"DAILY\"}");
        t.setEnabled(true);
        t.setDeleted(false);
        return t;
    }

    private TaskAssignment createAssignment(Long id, String status, LocalDateTime deadline) {
        TaskAssignment a = new TaskAssignment();
        a.setId(id);
        a.setFamilyId(1L);
        a.setChildId(10L);
        a.setTemplateId(1L);
        a.setStatus(status);
        a.setDeadline(deadline);
        a.setCancelled(false);
        return a;
    }
}
