package com.cutegoals.auth.service;

import com.cutegoals.auth.mapper.AccountMapper;
import com.cutegoals.auth.mapper.FamilyMemberMapper;
import com.cutegoals.auth.mapper.RoleBindingMapper;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.auth.Account;
import com.cutegoals.common.entity.family.FamilyMember;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;

/**
 * Authentication service for phone+password login.
 * Does not enumerate whether an account exists (uniform error responses).
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final AccountMapper accountMapper;
    private final RoleBindingMapper roleBindingMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final TokenService tokenService;
    private final AuditService auditService;

    /**
     * Authenticate with phone + password.
     *
     * @param phone    raw phone number
     * @param password raw password
     * @return LoginResult containing account info and tokens
     * @throws BusinessException with AUTHENTICATION_FAILED for any error
     */
    @Transactional
    public LoginResult login(String phone, String password) {
        // Rate limit check based on phone identifier
        try {
            checkRateLimit(phone);
        } catch (BusinessException e) {
            auditService.record(AuditEvent.RATE_LIMITED, null, "FAILED",
                    "Rate limited login attempt for phone=" + phone);
            throw e;
        }

        // Normalize phone
        String normalizedPhone;
        try {
            normalizedPhone = PhoneNormalizer.normalize(phone);
        } catch (IllegalArgumentException e) {
            auditService.record(AuditEvent.LOGIN_FAILED, null, "FAILED",
                    "Invalid phone format: " + phone);
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }

        Optional<Account> optAccount = accountMapper.findByPhone(normalizedPhone);

        // Uniform handling: same error for missing account, wrong password, or disabled account
        if (optAccount.isEmpty()) {
            auditService.record(AuditEvent.LOGIN_FAILED, null, "FAILED",
                    "Login attempt for non-existent phone");
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }

        Account account = optAccount.get();

        // Check account status
        if (!AuthConstants.STATUS_ACTIVE.equals(account.getStatus())) {
            auditService.record(AuditEvent.LOGIN_FAILED, account.getId(), "FAILED",
                    "Login attempt for inactive account");
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }

        // Verify password
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            auditService.record(AuditEvent.LOGIN_FAILED, account.getId(), "FAILED",
                    "Wrong password for accountId=" + account.getId());
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }

        // Get roles
        List<String> roles = roleBindingMapper.findRolesByAccountId(account.getId());

        // Create session
        String sessionId = sessionService.createSession(account.getId(), null);

        // Generate tokens
        String accessToken = tokenService.generateAccessToken(account.getId(), roles, sessionId);
        String refreshToken = tokenService.generateRefreshToken(sessionId);

        // Get family ID
        Long familyId = null;
        Optional<FamilyMember> member = familyMemberMapper.findByAccountIdAndRole(
                account.getId(), AuthConstants.ROLE_PARENT);
        if (member.isPresent()) {
            familyId = member.get().getFamilyId();
        }

        log.info("Login successful: accountId={}", account.getId());
        auditService.record(AuditEvent.LOGIN_SUCCESS, account.getId(), "SUCCESS",
                "Login successful for accountId=" + account.getId());

        return new LoginResult(account.getId(), account.getPhone(), roles, familyId,
                accessToken, refreshToken, AuthConstants.JWT_ACCESS_EXPIRY_MINUTES * 60);
    }

    /**
     * Result of a successful login.
     */
    // === Password Change ===

    /**
     * Change password for an account.
     * Verifies old password, validates new password complexity, updates hash, revokes all sessions.
     */
    @Transactional
    public void changePassword(Long accountId, String oldPassword, String newPassword) {
        Account account = accountMapper.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_FAILED));

        // Verify old password
        if (!passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
            log.warn("Password change failed: wrong old password for accountId={}", accountId);
            auditService.record(AuditEvent.PASSWORD_CHANGE_FAILED, accountId, "FAILED",
                    "Wrong old password for accountId=" + accountId);
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }

        // Validate new password complexity
        try {
            validatePasswordComplexity(newPassword);
        } catch (BusinessException e) {
            auditService.record(AuditEvent.PASSWORD_CHANGE_FAILED, accountId, "FAILED",
                    "New password failed complexity check for accountId=" + accountId);
            throw e;
        }

        // Update password hash
        String newHash = passwordEncoder.encode(newPassword);
        accountMapper.updatePassword(accountId, newHash);

        // Revoke all sessions
        sessionService.revokeAllSessions(accountId);

        auditService.record(AuditEvent.PASSWORD_CHANGE, accountId, "SUCCESS",
                "Password changed for accountId=" + accountId);
        log.info("Password changed successfully: accountId={}", accountId);
    }

    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATED);
        }
        // At least one letter and one digit
        if (!Pattern.compile("[a-zA-Z]").matcher(password).find()
                || !Pattern.compile("[0-9]").matcher(password).find()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATED);
        }
    }

    // === Rate Limiting ===

    private final ConcurrentHashMap<String, List<Long>> loginAttempts = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_ATTEMPTS = 10;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000; // 1 minute

    private void checkRateLimit(String identifier) {
        long now = System.currentTimeMillis();
        loginAttempts.compute(identifier, (key, timestamps) -> {
            if (timestamps == null) {
                List<Long> newList = new java.util.ArrayList<>();
                newList.add(now);
                return newList;
            }
            // Remove entries outside the window
            timestamps.removeIf(t -> (now - t) > RATE_LIMIT_WINDOW_MS);
            if (timestamps.size() >= MAX_LOGIN_ATTEMPTS) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
            timestamps.add(now);
            return timestamps;
        });
    }

    public record LoginResult(
            Long accountId,
            String phone,
            List<String> roles,
            Long familyId,
            String accessToken,
            String refreshToken,
            int expiresIn
    ) {}
}
