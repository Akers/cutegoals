package com.cutegoals.auth.service;

import com.cutegoals.auth.mapper.AccountMapper;
import com.cutegoals.auth.mapper.FamilyMemberMapper;
import com.cutegoals.auth.mapper.RoleBindingMapper;
import com.cutegoals.common.entity.auth.Account;
import com.cutegoals.common.entity.family.FamilyMember;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private RoleBindingMapper roleBindingMapper;

    @Mock
    private FamilyMemberMapper familyMemberMapper;

    @Mock
    private SessionService sessionService;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuditService auditService;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthenticationService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // Low cost for tests
        authService = new AuthenticationService(
                accountMapper, roleBindingMapper, familyMemberMapper,
                passwordEncoder, sessionService, tokenService, auditService);
    }

    @Test
    void shouldLoginWithValidCredentials() {
        String phone = "13800138000";
        String rawPassword = "correctPassword";
        String hashedPassword = passwordEncoder.encode(rawPassword);

        Account account = new Account();
        account.setId(1L);
        account.setPhone(phone);
        account.setPasswordHash(hashedPassword);
        account.setStatus("ACTIVE");

        FamilyMember member = new FamilyMember();
        member.setFamilyId(100L);

        when(accountMapper.findByPhone(phone)).thenReturn(Optional.of(account));
        when(roleBindingMapper.findRolesByAccountId(1L)).thenReturn(List.of("INSTANCE_ADMIN", "PARENT"));
        when(sessionService.createSession(anyLong(), any())).thenReturn("session-1");
        when(tokenService.generateAccessToken(anyLong(), anyList(), anyString())).thenReturn("access-token");
        when(tokenService.generateRefreshToken(anyString())).thenReturn("refresh-token");
        when(familyMemberMapper.findByAccountIdAndRole(1L, "PARENT")).thenReturn(Optional.of(member));

        var result = authService.login(phone, rawPassword);

        assertEquals(1L, result.accountId());
        assertEquals(phone, result.phone());
        assertEquals(2, result.roles().size());
        assertEquals(100L, result.familyId());
        assertNotNull(result.accessToken());
        assertNotNull(result.refreshToken());
    }

    @Test
    void shouldReturnAuthenticationFailedForWrongPassword() {
        String phone = "13800138000";
        Account account = new Account();
        account.setId(1L);
        account.setPhone(phone);
        account.setPasswordHash(passwordEncoder.encode("correctPassword"));
        account.setStatus("ACTIVE");

        when(accountMapper.findByPhone(phone)).thenReturn(Optional.of(account));

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login(phone, "wrongPassword"));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldReturnAuthenticationFailedForNonExistentPhone() {
        when(accountMapper.findByPhone(anyString())).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login("13800138000", "anyPassword"));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldReturnAuthenticationFailedForDisabledAccount() {
        String phone = "13800138000";
        Account account = new Account();
        account.setId(1L);
        account.setPhone(phone);
        account.setPasswordHash(passwordEncoder.encode("password"));
        account.setStatus("DISABLED");

        when(accountMapper.findByPhone(phone)).thenReturn(Optional.of(account));

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login(phone, "password"));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldReturnAuthenticationFailedForInvalidPhoneFormat() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login("invalid", "password"));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldNormalizePhoneWithPrefix() {
        String phone = "+8613800138000";
        String rawPassword = "password";
        String hashedPassword = passwordEncoder.encode(rawPassword);

        Account account = new Account();
        account.setId(1L);
        account.setPhone("13800138000");
        account.setPasswordHash(hashedPassword);
        account.setStatus("ACTIVE");

        when(accountMapper.findByPhone("13800138000")).thenReturn(Optional.of(account));
        when(roleBindingMapper.findRolesByAccountId(1L)).thenReturn(List.of("PARENT"));
        when(sessionService.createSession(anyLong(), any())).thenReturn("session-1");
        when(tokenService.generateAccessToken(anyLong(), anyList(), anyString())).thenReturn("access-token");
        when(tokenService.generateRefreshToken(anyString())).thenReturn("refresh-token");
        when(familyMemberMapper.findByAccountIdAndRole(1L, "PARENT")).thenReturn(Optional.empty());

        var result = authService.login(phone, rawPassword);
        assertEquals("13800138000", result.phone());
    }

    @Test
    void passwordEncoderShouldMatch() {
        String password = "mySecretPassword123!";
        String hash = passwordEncoder.encode(password);
        assertTrue(passwordEncoder.matches(password, hash));
        assertFalse(passwordEncoder.matches("wrongPassword", hash));
    }

    // === C1: Password Change Tests ===

    @Test
    void shouldChangePasswordSuccessfully() {
        Long accountId = 1L;
        String oldPassword = "oldPassword123";
        String newPassword = "newPassword456";
        String hashedOldPassword = passwordEncoder.encode(oldPassword);

        Account account = new Account();
        account.setId(accountId);
        account.setPasswordHash(hashedOldPassword);
        account.setStatus("ACTIVE");

        when(accountMapper.findById(accountId)).thenReturn(Optional.of(account));

        authService.changePassword(accountId, oldPassword, newPassword);

        verify(accountMapper).updatePassword(eq(accountId), anyString());
        verify(sessionService).revokeAllSessions(accountId);
        verify(auditService).record(eq(AuditEvent.PASSWORD_CHANGE), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldFailChangePasswordWithWrongOldPassword() {
        Long accountId = 1L;
        String hashedPassword = passwordEncoder.encode("correctPassword");

        Account account = new Account();
        account.setId(accountId);
        account.setPasswordHash(hashedPassword);

        when(accountMapper.findById(accountId)).thenReturn(Optional.of(account));

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.changePassword(accountId, "wrongPassword", "newPassword456"));
        assertEquals(ErrorCode.AUTHENTICATION_FAILED, e.getErrorCode());
        verify(auditService).record(eq(AuditEvent.PASSWORD_CHANGE_FAILED), eq(accountId), eq("FAILED"), anyString());
    }

    @Test
    void shouldFailChangePasswordWithWeakNewPassword() {
        Long accountId = 1L;
        String oldPassword = "oldPassword123";
        String hashedOldPassword = passwordEncoder.encode(oldPassword);

        Account account = new Account();
        account.setId(accountId);
        account.setPasswordHash(hashedOldPassword);

        when(accountMapper.findById(accountId)).thenReturn(Optional.of(account));

        // Too short
        BusinessException e1 = assertThrows(BusinessException.class,
                () -> authService.changePassword(accountId, oldPassword, "Ab1"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATED, e1.getErrorCode());

        // No digit
        BusinessException e2 = assertThrows(BusinessException.class,
                () -> authService.changePassword(accountId, oldPassword, "abcdefgh"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATED, e2.getErrorCode());

        // No letter
        BusinessException e3 = assertThrows(BusinessException.class,
                () -> authService.changePassword(accountId, oldPassword, "12345678"));
        assertEquals(ErrorCode.PASSWORD_POLICY_VIOLATED, e3.getErrorCode());
    }

    // === I3: Rate Limiting Tests ===

    @Test
    void shouldRateLimitAfterTooManyAttempts() {
        String phone = "13800138000";
        String password = "anyPassword";

        // First 10 attempts should not be rate-limited (even if login fails for other reasons)
        when(accountMapper.findByPhone(phone)).thenReturn(Optional.empty());

        // 10 failed attempts
        for (int i = 0; i < 10; i++) {
            assertThrows(BusinessException.class, () -> authService.login(phone, password));
        }

        // 11th attempt should be rate limited
        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login(phone, "anything"));
        assertEquals(ErrorCode.RATE_LIMITED, e.getErrorCode());
    }
}
