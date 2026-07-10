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
        // Normalize phone
        String normalizedPhone;
        try {
            normalizedPhone = PhoneNormalizer.normalize(phone);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }

        Optional<Account> optAccount = accountMapper.findByPhone(normalizedPhone);

        // Uniform handling: same error for missing account, wrong password, or disabled account
        if (optAccount.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }

        Account account = optAccount.get();

        // Check account status
        if (!AuthConstants.STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }

        // Verify password
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
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

        return new LoginResult(account.getId(), account.getPhone(), roles, familyId,
                accessToken, refreshToken, AuthConstants.JWT_ACCESS_EXPIRY_MINUTES * 60);
    }

    /**
     * Result of a successful login.
     */
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
