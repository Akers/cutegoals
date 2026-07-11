package com.cutegoals.instancemanagement.service;

import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.instance.AuditLog;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.instancemanagement.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed audit service implementation.
 * <p>
 * Writes audit events to the {@code audit_log} table via MyBatis-Plus.
 * Activated when {@code app.audit.type=db} is set in configuration.
 * Falls back to {@link com.cutegoals.auth.service.InMemoryAuditService} otherwise.
 * <p>
 * This resolves the data source mismatch where {@link AuditLogService} queries
 * the {@code audit_log} table but events were only written to memory.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.audit.type", havingValue = "db")
public class DatabaseAuditService implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAuditService.class);

    private final AuditLogMapper auditLogMapper;

    /**
     * Record an audit event by inserting into the {@code audit_log} table.
     * Uses {@link Propagation#MANDATORY} to ensure the caller's transaction
     * is active, so a failure here will roll back the entire operation.
     *
     * @param eventType the event type (see {@link com.cutegoals.auth.service.AuditEvent})
     * @param actorId   the acting account ID (null for system/anonymous actions)
     * @param result    "SUCCESS" or "FAILED"
     * @param summary   human-readable summary
     * @throws BusinessException with {@link ErrorCode#AUDIT_UNAVAILABLE} if the DB write fails
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String eventType, Long actorId, String result, String summary) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorId(actorId);
        auditLog.setEventType(eventType);
        auditLog.setResult(result);
        auditLog.setSummary(summary);
        auditLog.setRequestId(MDC.get("requestId"));

        try {
            auditLogMapper.insert(auditLog);
            log.debug("AUDIT DB: event={}, actorId={}, result={}, summary={}", eventType, actorId, result, summary);
        } catch (Exception e) {
            log.error("Failed to persist audit event: event={}, actorId={}, result={}",
                    eventType, actorId, result, e);
            throw new BusinessException(ErrorCode.AUDIT_UNAVAILABLE,
                    "Failed to persist audit event: " + eventType, e);
        }
    }
}
