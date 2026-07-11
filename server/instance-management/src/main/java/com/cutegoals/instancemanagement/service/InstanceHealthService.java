package com.cutegoals.instancemanagement.service;

import com.cutegoals.auth.service.InitializationTokenService;
import com.cutegoals.common.entity.instance.BackupRun;
import com.cutegoals.common.entity.instance.RecoveryDrill;
import com.cutegoals.instancemanagement.mapper.BackupRunMapper;
import com.cutegoals.instancemanagement.mapper.RecoveryDrillMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for instance health and status information (Task 6.1).
 */
@Service
@RequiredArgsConstructor
public class InstanceHealthService {

    private static final Logger log = LoggerFactory.getLogger(InstanceHealthService.class);

    private final InitializationTokenService tokenService;
    private final BackupRunMapper backupRunMapper;
    private final RecoveryDrillMapper recoveryDrillMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.version:2.0.0-SNAPSHOT}")
    private String appVersion;

    @Value("${app.build-time:unknown}")
    private String buildTime;

    @Value("${app.build-commit:unknown}")
    private String buildCommit;

    /**
     * Public health check - no auth required, minimal status.
     */
    public Map<String, Object> getPublicHealth() {
        boolean dbHealthy = checkDatabaseHealth();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", dbHealthy ? "UP" : "DOWN");
        data.put("initialized", tokenService.isInitialized());
        return data;
    }

    /**
     * Admin health check - detailed status including backups and recovery drills.
     */
    public Map<String, Object> getAdminHealth() {
        boolean dbHealthy = checkDatabaseHealth();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", dbHealthy ? "UP" : "DOWN");
        data.put("initialized", tokenService.isInitialized());
        data.put("version", appVersion);
        data.put("buildTime", buildTime);
        data.put("buildCommit", buildCommit);

        // Database migration status (simplified - check if we can read from DB)
        data.put("database", Map.of(
                "status", dbHealthy ? "UP" : "DOWN",
                "type", "mysql"
        ));

        // Backup status
        Map<String, Object> backupStatus = getBackupStatus();
        data.put("backup", backupStatus);

        // Recovery drill result
        Map<String, Object> drillStatus = getRecoveryDrillStatus();
        data.put("recoveryDrill", drillStatus);

        // RPO check
        if (backupStatus.get("lastBackupTime") instanceof LocalDateTime lastBackup) {
            long hoursSinceBackup = Duration.between(lastBackup, LocalDateTime.now()).toHours();
            if (hoursSinceBackup > 24) {
                data.put("rpoWarning", "RPO_EXCEEDED");
                data.put("rpoWarningMessage",
                        "RPO exceeded 24 hours: last successful backup was " + hoursSinceBackup + " hours ago");
            }
        }

        return data;
    }

    /**
     * Check database health by executing {@code SELECT 1}.
     * Does NOT depend on any specific table (e.g. backup_run), so it works
     * correctly on first deployment before any backup data exists.
     */
    private boolean checkDatabaseHealth() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get backup status from the latest backup_run record.
     */
    private Map<String, Object> getBackupStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Optional<BackupRun> latest = backupRunMapper.findLatest();
            if (latest.isPresent()) {
                BackupRun run = latest.get();
                result.put("lastBackupTime", run.getCompletedAt() != null ? run.getCompletedAt() : run.getStartedAt());
                result.put("lastBackupStatus", run.getStatus());
                // Next scheduled backup is typically at 02:00 daily
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime nextBackup = now.toLocalDate().plusDays(1).atTime(2, 0);
                if (now.getHour() >= 2) {
                    nextBackup = now.toLocalDate().plusDays(1).atTime(2, 0);
                } else {
                    nextBackup = now.toLocalDate().atTime(2, 0);
                }
                result.put("nextScheduledBackup", nextBackup);
            } else {
                result.put("lastBackupTime", null);
                result.put("lastBackupStatus", "NEVER");
                result.put("nextScheduledBackup", LocalDateTime.now().toLocalDate().plusDays(1).atTime(2, 0));
            }
        } catch (Exception e) {
            log.warn("Failed to read backup status: {}", e.getMessage());
            result.put("lastBackupTime", null);
            result.put("lastBackupStatus", "ERROR");
            result.put("nextScheduledBackup", null);
        }
        return result;
    }

    /**
     * Get recovery drill status from the latest recovery_drill record.
     */
    private Map<String, Object> getRecoveryDrillStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Optional<RecoveryDrill> latest = recoveryDrillMapper.findLatest();
            if (latest.isPresent()) {
                RecoveryDrill drill = latest.get();
                result.put("lastRecoveryDrillTime", drill.getCompletedAt());
                result.put("lastRecoveryDrillStatus", Boolean.TRUE.equals(drill.getSuccess()) ? "SUCCESS" : "FAILED");
                result.put("rpoSeconds", drill.getRpoSeconds());
                result.put("rtoSeconds", drill.getRtoSeconds());
            } else {
                result.put("lastRecoveryDrillTime", null);
                result.put("lastRecoveryDrillStatus", "NEVER");
                result.put("rpoSeconds", null);
                result.put("rtoSeconds", null);
            }
        } catch (Exception e) {
            log.warn("Failed to read recovery drill status: {}", e.getMessage());
            result.put("lastRecoveryDrillTime", null);
            result.put("lastRecoveryDrillStatus", "ERROR");
            result.put("rpoSeconds", null);
            result.put("rtoSeconds", null);
        }
        return result;
    }
}
