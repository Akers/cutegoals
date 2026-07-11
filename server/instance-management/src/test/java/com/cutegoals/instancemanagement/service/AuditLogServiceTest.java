package com.cutegoals.instancemanagement.service;

import com.cutegoals.common.entity.instance.AuditLog;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.instancemanagement.mapper.AuditLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AuditLogService focusing on:
 * - Sensitive field masking with exact key matching (I3)
 * - Query pagination and date range limits
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogMapper);
    }

    @Test
    void shouldRejectPageSizeExceedingMax() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.queryAuditLogs(null, null, null, null, null, 1, 101));
        assertEquals(ErrorCode.AUDIT_QUERY_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void shouldRejectDateRangeExceedingMax() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> auditLogService.queryAuditLogs(null, null, null,
                        LocalDateTime.now().minusDays(400).toLocalDate(),
                        LocalDateTime.now().toLocalDate(), 1, 20));
        assertEquals(ErrorCode.AUDIT_QUERY_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    // === maskSensitiveFields (I3) ===

    @Test
    void shouldIncludeMetadataWithoutSensitiveKeys() {
        AuditLog log = createLog("{\"description\":\"test action\",\"reason\":\"admin request\"}");

        Map<String, Object> result = invokeMaskSensitiveFields(log);

        assertNotNull(result.get("metadata"));
    }

    @Test
    void shouldMaskMetadataWithPasswordKey() {
        AuditLog log = createLog("{\"passwordHash\":\"$2a$10$xxx\"}");

        Map<String, Object> result = invokeMaskSensitiveFields(log);

        assertNull(result.get("metadata"));
    }

    @Test
    void shouldMaskMetadataWithPinKey() {
        AuditLog log = createLog("{\"pinHash\":\"$2a$10$yyy\"}");

        Map<String, Object> result = invokeMaskSensitiveFields(log);

        assertNull(result.get("metadata"));
    }

    @Test
    void shouldMaskMetadataWithTokenKey() {
        AuditLog log = createLog("{\"refreshToken\":\"abc123\"}");

        Map<String, Object> result = invokeMaskSensitiveFields(log);

        assertNull(result.get("metadata"));
    }

    @Test
    void shouldNotFalsePositiveOnPassport() {
        // "passport" contains "passport" but should NOT match exact key "passwordHash"
        AuditLog log = createLog("{\"passport\":\"12345\",\"spinning\":\"top\"}");

        Map<String, Object> result = invokeMaskSensitiveFields(log);

        assertNotNull(result.get("metadata"), "Metadata with 'passport' should not be masked");
    }

    @Test
    void shouldNotFalsePositiveOnDescription() {
        // "description" contains no sensitive keys
        AuditLog log = createLog("{\"description\":\"token rotation completed\"}");

        Map<String, Object> result = invokeMaskSensitiveFields(log);

        assertNotNull(result.get("metadata"), "Metadata with 'description' should not be masked");
    }

    @Test
    void shouldMaskPlainTextMetadataWithSensitiveWord() {
        AuditLog log = createLog("password changed for account 1");

        Map<String, Object> result = invokeMaskSensitiveFields(log);

        assertNull(result.get("metadata"), "Plain text containing 'password' should be masked");
    }

    @Test
    void shouldIncludePlainTextMetadataWithoutSensitiveWords() {
        AuditLog log = createLog("config key updated: max_login_attempts");

        Map<String, Object> result = invokeMaskSensitiveFields(log);

        assertNotNull(result.get("metadata"));
    }

    @Test
    void shouldHandleNullMetadata() {
        AuditLog log = createLog(null);

        Map<String, Object> result = invokeMaskSensitiveFields(log);

        assertNull(result.get("metadata"));
    }

    // === Helper: use reflection to call private method ===

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeMaskSensitiveFields(AuditLog log) {
        try {
            var method = AuditLogService.class.getDeclaredMethod("maskSensitiveFields", AuditLog.class);
            method.setAccessible(true);
            return (Map<String, Object>) method.invoke(auditLogService, log);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke maskSensitiveFields", e);
        }
    }

    private AuditLog createLog(String metadata) {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setActorId(1L);
        log.setEventType("TEST_EVENT");
        log.setResult("SUCCESS");
        log.setSummary("test event");
        log.setRequestId("req-123");
        log.setCreatedAt(LocalDateTime.now());
        log.setMetadata(metadata);
        return log;
    }
}
