package com.cutegoals.instancemanagement.service;

import com.cutegoals.auth.service.InitializationTokenService;
import com.cutegoals.common.entity.instance.BackupRun;
import com.cutegoals.common.entity.instance.RecoveryDrill;
import com.cutegoals.instancemanagement.mapper.BackupRunMapper;
import com.cutegoals.instancemanagement.mapper.RecoveryDrillMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstanceHealthServiceTest {

    @Mock
    private InitializationTokenService tokenService;

    @Mock
    private BackupRunMapper backupRunMapper;

    @Mock
    private RecoveryDrillMapper recoveryDrillMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private InstanceHealthService healthService;

    @BeforeEach
    void setUp() {
        healthService = new InstanceHealthService(tokenService, backupRunMapper, recoveryDrillMapper, jdbcTemplate);
        // Set @Value fields via reflection since Spring is not active in unit tests
        setField(healthService, "appVersion", "2.0.0-TEST");
        setField(healthService, "buildTime", "2026-07-11T00:00:00");
        setField(healthService, "buildCommit", "abc123");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    @Test
    void shouldReturnPublicHealth() {
        when(tokenService.isInitialized()).thenReturn(true);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        Map<String, Object> health = healthService.getPublicHealth();

        assertEquals("UP", health.get("status"));
        assertTrue((Boolean) health.get("initialized"));
    }

    @Test
    void shouldReturnAdminHealthWithBackupInfo() {
        when(tokenService.isInitialized()).thenReturn(true);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        BackupRun backupRun = new BackupRun();
        backupRun.setStatus("SUCCESS");
        backupRun.setCompletedAt(LocalDateTime.now().minusHours(2));
        when(backupRunMapper.findLatest()).thenReturn(Optional.of(backupRun));

        RecoveryDrill drill = new RecoveryDrill();
        drill.setSuccess(true);
        drill.setCompletedAt(LocalDateTime.now().minusDays(7));
        drill.setRpoSeconds(7200);
        drill.setRtoSeconds(300);
        when(recoveryDrillMapper.findLatest()).thenReturn(Optional.of(drill));

        Map<String, Object> health = healthService.getAdminHealth();

        assertEquals("UP", health.get("status"));
        assertTrue((Boolean) health.get("initialized"));
        assertNotNull(health.get("version"));
        assertNotNull(health.get("backup"));
        assertNotNull(health.get("recoveryDrill"));

        @SuppressWarnings("unchecked")
        Map<String, Object> backup = (Map<String, Object>) health.get("backup");
        assertEquals("SUCCESS", backup.get("lastBackupStatus"));

        @SuppressWarnings("unchecked")
        Map<String, Object> drillResult = (Map<String, Object>) health.get("recoveryDrill");
        assertEquals("SUCCESS", drillResult.get("lastRecoveryDrillStatus"));
        assertEquals(7200, drillResult.get("rpoSeconds"));
        assertEquals(300, drillResult.get("rtoSeconds"));

        // No RPO warning since backup was 2 hours ago
        assertNull(health.get("rpoWarning"));
    }

    @Test
    void shouldWarnWhenRPOExceeds24Hours() {
        when(tokenService.isInitialized()).thenReturn(true);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        BackupRun backupRun = new BackupRun();
        backupRun.setStatus("SUCCESS");
        backupRun.setCompletedAt(LocalDateTime.now().minusHours(48));
        when(backupRunMapper.findLatest()).thenReturn(Optional.of(backupRun));

        when(recoveryDrillMapper.findLatest()).thenReturn(Optional.empty());

        Map<String, Object> health = healthService.getAdminHealth();

        assertEquals("RPO_EXCEEDED", health.get("rpoWarning"));
    }

    @Test
    void shouldHandleNoBackupRun() {
        when(tokenService.isInitialized()).thenReturn(true);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(backupRunMapper.findLatest()).thenReturn(Optional.empty());
        when(recoveryDrillMapper.findLatest()).thenReturn(Optional.empty());

        Map<String, Object> health = healthService.getAdminHealth();

        @SuppressWarnings("unchecked")
        Map<String, Object> backup = (Map<String, Object>) health.get("backup");
        assertEquals("NEVER", backup.get("lastBackupStatus"));
    }

    @Test
    void shouldHandleDatabaseError() {
        when(tokenService.isInitialized()).thenReturn(true);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new RuntimeException("DB connection failed"));
        when(backupRunMapper.findLatest()).thenReturn(Optional.empty());
        when(recoveryDrillMapper.findLatest()).thenReturn(Optional.empty());

        Map<String, Object> health = healthService.getAdminHealth();

        assertEquals("DOWN", health.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> backup = (Map<String, Object>) health.get("backup");
        assertEquals("NEVER", backup.get("lastBackupStatus"));
    }
}
