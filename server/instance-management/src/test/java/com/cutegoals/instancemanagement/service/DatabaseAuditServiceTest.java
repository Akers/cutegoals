package com.cutegoals.instancemanagement.service;

import com.cutegoals.common.entity.instance.AuditLog;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.instancemanagement.mapper.AuditLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseAuditServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @Captor
    private ArgumentCaptor<AuditLog> auditLogCaptor;

    private DatabaseAuditService databaseAuditService;

    @BeforeEach
    void setUp() {
        databaseAuditService = new DatabaseAuditService(auditLogMapper);
    }

    @Test
    void shouldPersistAuditEvent() {
        databaseAuditService.record("LOGIN_SUCCESS", 1L, "SUCCESS", "Login by accountId=1");

        verify(auditLogMapper).insert(auditLogCaptor.capture());
        AuditLog persisted = auditLogCaptor.getValue();
        assertEquals("LOGIN_SUCCESS", persisted.getEventType());
        assertEquals(1L, persisted.getActorId());
        assertEquals("SUCCESS", persisted.getResult());
        assertEquals("Login by accountId=1", persisted.getSummary());
    }

    @Test
    void shouldThrowAuditUnavailableOnDbFailure() {
        doThrow(new RuntimeException("Connection refused"))
                .when(auditLogMapper).insert(any(AuditLog.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> databaseAuditService.record("TEST_EVENT", 1L, "SUCCESS", "Test"));
        assertEquals(ErrorCode.AUDIT_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    void shouldHandleNullActorId() {
        databaseAuditService.record("SYSTEM_EVENT", null, "SUCCESS", "System action");

        verify(auditLogMapper).insert(auditLogCaptor.capture());
        assertNull(auditLogCaptor.getValue().getActorId());
    }

    @Test
    void shouldHandleFailedResult() {
        databaseAuditService.record("LOGIN_FAILED", 1L, "FAILED", "Wrong password");

        verify(auditLogMapper).insert(auditLogCaptor.capture());
        assertEquals("FAILED", auditLogCaptor.getValue().getResult());
    }
}
