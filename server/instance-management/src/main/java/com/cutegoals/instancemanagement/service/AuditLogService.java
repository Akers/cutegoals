package com.cutegoals.instancemanagement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cutegoals.common.entity.instance.AuditLog;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.instancemanagement.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for audit log query and export (Task 6.4).
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_DAYS = 90; // Max query range for export
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int QUERY_MAX_DAYS = 365; // Max query range for paginated queries

    /**
     * Query audit logs with filters and pagination.
     */
    public Map<String, Object> queryAuditLogs(
            String actorId, String eventType, String result,
            LocalDate startDate, LocalDate endDate,
            int pageNum, int pageSize) {

        if (pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.AUDIT_QUERY_LIMIT_EXCEEDED,
                    "Page size cannot exceed " + MAX_PAGE_SIZE);
        }

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();

        if (actorId != null && !actorId.isBlank()) {
            wrapper.eq(AuditLog::getActorId, actorId);
        }
        if (eventType != null && !eventType.isBlank()) {
            wrapper.eq(AuditLog::getEventType, eventType);
        }
        if (result != null && !result.isBlank()) {
            wrapper.eq(AuditLog::getResult, result);
        }

        LocalDateTime startDateTime = startDate != null
                ? startDate.atStartOfDay()
                : LocalDateTime.now().minusDays(QUERY_MAX_DAYS);
        LocalDateTime endDateTime = endDate != null
                ? endDate.atTime(LocalTime.MAX)
                : LocalDateTime.now();

        // Validate date range - prevent scanning too much data
        long daysBetween = java.time.Duration.between(startDateTime, endDateTime).toDays();
        if (daysBetween > QUERY_MAX_DAYS) {
            throw new BusinessException(ErrorCode.AUDIT_QUERY_LIMIT_EXCEEDED,
                    "Query range cannot exceed " + QUERY_MAX_DAYS + " days");
        }

        wrapper.ge(AuditLog::getCreatedAt, startDateTime);
        wrapper.le(AuditLog::getCreatedAt, endDateTime);
        wrapper.orderByDesc(AuditLog::getCreatedAt);

        Page<AuditLog> page = auditLogMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        List<Map<String, Object>> maskedLogs = page.getRecords().stream()
                .map(this::maskSensitiveFields)
                .toList();

        Map<String, Object> result2 = new LinkedHashMap<>();
        result2.put("content", maskedLogs);
        result2.put("page", page.getCurrent());
        result2.put("pageSize", page.getSize());
        result2.put("totalElements", page.getTotal());
        result2.put("totalPages", page.getPages());
        return result2;
    }

    /**
     * Export audit logs with desensitization.
     */
    public List<Map<String, Object>> exportAuditLogs(
            String actorId, String eventType, String result,
            LocalDate startDate, LocalDate endDate) {

        LocalDateTime startDateTime = startDate != null
                ? startDate.atStartOfDay()
                : LocalDateTime.now().minusDays(MAX_EXPORT_DAYS);
        LocalDateTime endDateTime = endDate != null
                ? endDate.atTime(LocalTime.MAX)
                : LocalDateTime.now();

        long daysBetween = java.time.Duration.between(startDateTime, endDateTime).toDays();
        if (daysBetween > MAX_EXPORT_DAYS) {
            throw new BusinessException(ErrorCode.AUDIT_QUERY_LIMIT_EXCEEDED,
                    "Export range cannot exceed " + MAX_EXPORT_DAYS + " days");
        }

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (actorId != null && !actorId.isBlank()) {
            wrapper.eq(AuditLog::getActorId, actorId);
        }
        if (eventType != null && !eventType.isBlank()) {
            wrapper.eq(AuditLog::getEventType, eventType);
        }
        if (result != null && !result.isBlank()) {
            wrapper.eq(AuditLog::getResult, result);
        }
        wrapper.ge(AuditLog::getCreatedAt, startDateTime);
        wrapper.le(AuditLog::getCreatedAt, endDateTime);
        wrapper.orderByDesc(AuditLog::getCreatedAt);

        List<AuditLog> logs = auditLogMapper.selectList(wrapper);

        return logs.stream()
                .map(this::maskSensitiveFields)
                .toList();
    }

    /** Exact field names that are considered sensitive and must not appear in metadata. */
    private static final Set<String> SENSITIVE_METADATA_KEYS = Set.of(
            "passwordHash", "password", "pinHash", "pin", "token", "secret",
            "refreshToken", "accessToken", "jwtSecret", "plainToken", "phone");

    /**
     * Mask sensitive fields before returning audit log entries.
     * Uses exact field name matching (not substring) to avoid false positives
     * on innocuous keys like "description" or "passport".
     */
    private Map<String, Object> maskSensitiveFields(AuditLog log) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", log.getId());
        result.put("actorId", log.getActorId());
        result.put("actorType", log.getActorType());
        result.put("eventType", log.getEventType());
        result.put("result", log.getResult());
        result.put("objectType", log.getObjectType());
        result.put("objectId", log.getObjectId());
        result.put("summary", log.getSummary());
        result.put("requestId", log.getRequestId());
        result.put("createdAt", log.getCreatedAt());
        // Include metadata only if it does NOT contain exact sensitive field names.
        // Parses JSON metadata to check keys against the SENSITIVE_METADATA_KEYS set.
        if (log.getMetadata() != null && !containsSensitiveField(log.getMetadata())) {
            result.put("metadata", log.getMetadata());
        }
        return result;
    }

    /**
     * Checks if the metadata string contains any sensitive field names using
     * exact key matching. For JSON metadata ({@code {...}}), extracts keys and
     * checks against {@link #SENSITIVE_METADATA_KEYS}. For plain text, uses
     * word-boundary matching to avoid substring false positives.
     */
    private boolean containsSensitiveField(String metadata) {
        String trimmed = metadata.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // Probable JSON — extract keys and check against the exact set
            for (String key : SENSITIVE_METADATA_KEYS) {
                // Match JSON key patterns: "keyName":
                if (trimmed.contains("\"" + key + "\":")) {
                    return true;
                }
            }
            return false;
        }
        // Plain text — use word-boundary matching to avoid substring false positives
        for (String key : SENSITIVE_METADATA_KEYS) {
            if (trimmed.matches(".*\\b" + java.util.regex.Pattern.quote(key) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }
}
