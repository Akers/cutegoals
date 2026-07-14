package com.cutegoals.task.scheduler;

import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.task.TaskAssignment;
import com.cutegoals.common.entity.task.TaskTemplate;
import com.cutegoals.task.mapper.TaskAssignmentMapper;
import com.cutegoals.task.mapper.TaskTemplateMapper;
import com.cutegoals.task.service.TaskTemplateFrequencyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Daily scheduler for REPEAT task type state transitions.
 * <p>
 * Runs at 00:05 Asia/Shanghai every day.
 * <p>
 * Operations:
 * 1. OPEN → EXPIRED: assignments past their deadline
 * 2. PENDING_OPEN → OPEN: assignments whose trigger date has arrived
 */
@Component
@RequiredArgsConstructor
public class RepeatTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(RepeatTaskScheduler.class);

    private final TaskTemplateMapper taskTemplateMapper;
    private final TaskAssignmentMapper taskAssignmentMapper;
    private final TaskTemplateFrequencyService taskTemplateFrequencyService;
    private final AuditService auditService;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    public void processRepeatAssignments() {
        log.info("REPEAT scheduler: starting daily processing");

        List<TaskTemplate> repeatTemplates = taskTemplateMapper.findEnabledRepeatTemplates();
        if (repeatTemplates.isEmpty()) {
            log.debug("REPEAT scheduler: no enabled REPEAT templates found");
            auditService.record(AuditEvent.REPEAT_SCHEDULER_RUN, null, "SUCCESS",
                    "REPEAT scheduler run: no enabled REPEAT templates");
            return;
        }

        int totalExpired = 0;
        int totalOpened = 0;
        int templateCount = 0;

        LocalDate today = LocalDate.now();

        for (TaskTemplate template : repeatTemplates) {
            try {
                // Step 1: Expire OPEN assignments past deadline
                List<TaskAssignment> expiredOpen = taskAssignmentMapper
                        .findExpiredOpenByTemplate(template.getId());
                int expired = 0;
                for (TaskAssignment a : expiredOpen) {
                    int updated = taskAssignmentMapper.expireAssignment(a.getId());
                    if (updated > 0) {
                        expired++;
                    }
                }
                totalExpired += expired;

                // Step 2: Open PENDING_OPEN assignments whose trigger date has arrived
                List<TaskAssignment> pendingOpen = taskAssignmentMapper
                        .findPendingOpenByTemplate(template.getId());
                int opened = 0;
                for (TaskAssignment a : pendingOpen) {
                    LocalDate triggerDate = a.getDeadline() != null
                            ? a.getDeadline().toLocalDate()
                            : null;
                    if (triggerDate != null && !triggerDate.isAfter(today)) {
                        int updated = taskAssignmentMapper.openAssignment(a.getId());
                        if (updated > 0) {
                            opened++;
                        }
                    }
                }
                totalOpened += opened;
                templateCount++;

                if (expired > 0 || opened > 0) {
                    log.info("REPEAT scheduler: templateId={}, expired={}, opened={}",
                            template.getId(), expired, opened);
                }
            } catch (Exception e) {
                log.error("REPEAT scheduler: error processing templateId={}", template.getId(), e);
            }
        }

        auditService.record(AuditEvent.REPEAT_SCHEDULER_RUN, null, "SUCCESS",
                "REPEAT scheduler run: templates=" + templateCount
                        + ", expired=" + totalExpired + ", opened=" + totalOpened);

        log.info("REPEAT scheduler: completed — templates={}, expired={}, opened={}",
                templateCount, totalExpired, totalOpened);
    }
}
