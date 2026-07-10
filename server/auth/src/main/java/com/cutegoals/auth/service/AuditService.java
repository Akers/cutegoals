package com.cutegoals.auth.service;

/**
 * Service interface for recording audit events.
 * Current implementation is in-memory; future versions should persist to the audit_log table.
 */
public interface AuditService {

    /**
     * Record an audit event.
     *
     * @param eventType the event type (see {@link AuditEvent})
     * @param actorId   the acting account ID (null for system/anonymous actions)
     * @param result    "SUCCESS" or "FAILED"
     * @param summary   human-readable summary
     */
    void record(String eventType, Long actorId, String result, String summary);
}
