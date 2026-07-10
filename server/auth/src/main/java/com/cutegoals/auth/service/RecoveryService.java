package com.cutegoals.auth.service;

import com.cutegoals.auth.mapper.AccountMapper;
import com.cutegoals.auth.mapper.RoleBindingMapper;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for local admin recovery flow.
 * Stores recovery tokens in a local in-memory map (no Redis needed).
 * Recovery tokens are one-time use and expire after 15 minutes.
 */
@Service
@RequiredArgsConstructor
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountMapper accountMapper;
    private final RoleBindingMapper roleBindingMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final AuditService auditService;

    // In-memory store for recovery tokens: token_hash -> RecoveryContext
    // In production, this could be moved to Redis
    private final ConcurrentHashMap<String, RecoveryContext> recoveryStore = new ConcurrentHashMap<>();

    /**
     * Initiate a recovery flow. Only works locally (enforced by caller).
     *
     * @return the plaintext recovery token (displayed to admin on console)
     */
    public String initiateRecovery() {
        byte[] bytes = new byte[AuthConstants.RECOVERY_TOKEN_BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        String plainToken = HexFormat.of().formatHex(bytes);

        String tokenHash = hashToken(plainToken);
        RecoveryContext ctx = new RecoveryContext(
                tokenHash,
                LocalDateTime.now().plusMinutes(AuthConstants.RECOVERY_TOKEN_EXPIRY_MINUTES)
        );

        recoveryStore.put(tokenHash, ctx);

        log.info("Recovery flow initiated, token hash={}", tokenHash);
        auditService.record(AuditEvent.RECOVERY_INITIATED, null, "SUCCESS",
                "Recovery flow initiated");
        return plainToken;
    }

    /**
     * Complete recovery with a new password.
     *
     * @param recoveryToken the one-time recovery token
     * @param newPassword   the new password
     * @throws BusinessException if token invalid/expired
     */
    @Transactional
    public void completeRecovery(String recoveryToken, String newPassword) {
        String tokenHash = hashToken(recoveryToken);

        RecoveryContext ctx = recoveryStore.remove(tokenHash);
        if (ctx == null) {
            auditService.record(AuditEvent.RECOVERY_FAILED, null, "FAILED",
                    "Invalid recovery token");
            throw new BusinessException(ErrorCode.RECOVERY_NOT_AVAILABLE);
        }

        if (ctx.expiresAt().isBefore(LocalDateTime.now())) {
            auditService.record(AuditEvent.RECOVERY_FAILED, null, "FAILED",
                    "Expired recovery token");
            throw new BusinessException(ErrorCode.RECOVERY_NOT_AVAILABLE);
        }

        // Find the first account with INSTANCE_ADMIN role
        // For MVP, this is the first admin account
        var accounts = accountMapper.selectList(null);
        Long adminAccountId = null;
        for (var acct : accounts) {
            var roles = roleBindingMapper.findRolesByAccountId(acct.getId());
            if (roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
                adminAccountId = acct.getId();
                break;
            }
        }

        if (adminAccountId == null) {
            auditService.record(AuditEvent.RECOVERY_FAILED, null, "FAILED",
                    "No admin account found for recovery");
            throw new BusinessException(ErrorCode.RECOVERY_NOT_AVAILABLE);
        }

        // Update password
        var adminAccount = accountMapper.findById(adminAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOVERY_NOT_AVAILABLE));
        adminAccount.setPasswordHash(passwordEncoder.encode(newPassword));
        accountMapper.updateById(adminAccount);

        // Revoke all sessions
        sessionService.revokeAllSessions(adminAccountId);

        auditService.record(AuditEvent.RECOVERY_SUCCESS, adminAccountId, "SUCCESS",
                "Recovery completed for accountId=" + adminAccountId);
        log.info("Recovery completed for accountId={}", adminAccountId);
    }

    private static String hashToken(String plainToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }

    private record RecoveryContext(String tokenHash, LocalDateTime expiresAt) {}
}
