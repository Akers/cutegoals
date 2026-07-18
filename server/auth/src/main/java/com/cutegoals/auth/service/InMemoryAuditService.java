package com.cutegoals.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory audit service implementation.
 * Stores audit events in memory for testing and single-instance deployments.
 * Can be replaced with a persistent implementation in production.
 */
@Service
@ConditionalOnProperty(name = "app.audit.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryAuditService implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAuditService.class);
    private final CopyOnWriteArrayList<AuditRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void record(String eventType, Long actorId, String result, String summary) {
        log.warn("AUDIT IN-MEMORY (data NOT persisted!): event={}, actorId={}, result={}, summary={}",
                eventType, actorId, result, summary);
        AuditRecord record = new AuditRecord(eventType, actorId, result, summary, System.currentTimeMillis());
        records.add(record);
    }

    /**
     * Get all recorded audit events (for testing).
     */
    public List<AuditRecord> getRecords() {
        return List.copyOf(records);
    }

    /**
     * Clear all recorded audit events (for testing).
     */
    public void clear() {
        records.clear();
    }

    public record AuditRecord(String eventType, Long actorId, String result, String summary, long timestamp) {}
}
