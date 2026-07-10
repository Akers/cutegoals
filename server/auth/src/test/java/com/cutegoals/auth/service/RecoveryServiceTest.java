package com.cutegoals.auth.service;

import com.cutegoals.auth.mapper.AccountMapper;
import com.cutegoals.auth.mapper.RoleBindingMapper;
import com.cutegoals.common.entity.auth.Account;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.cutegoals.common.entity.family.FamilyMember;
import com.cutegoals.common.entity.auth.RoleBinding;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RecoveryService.
 */
@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private RoleBindingMapper roleBindingMapper;

    @Mock
    private SessionService sessionService;

    @Mock
    private AuditService auditService;

    private BCryptPasswordEncoder passwordEncoder;
    private RecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        recoveryService = new RecoveryService(accountMapper, roleBindingMapper, passwordEncoder, sessionService, auditService);
    }

    @Test
    void shouldInitiateRecovery() {
        String token = recoveryService.initiateRecovery();
        assertNotNull(token);
        assertEquals(64, token.length()); // 32 bytes = 64 hex chars
    }

    @Test
    void shouldCompleteRecovery() {
        // Arrange: create admin account in DB
        Account admin = new Account();
        admin.setId(1L);
        admin.setPasswordHash(passwordEncoder.encode("oldPassword"));

        when(accountMapper.selectList(any())).thenReturn(List.of(admin));
        when(roleBindingMapper.findRolesByAccountId(1L)).thenReturn(List.of("INSTANCE_ADMIN"));
        when(accountMapper.findById(1L)).thenReturn(java.util.Optional.of(admin));
        when(accountMapper.updateById(any(Account.class))).thenReturn(1);

        // Initiate
        String token = recoveryService.initiateRecovery();

        // Complete
        assertDoesNotThrow(() -> recoveryService.completeRecovery(token, "newPassword"));

        // Verify password was changed
        verify(accountMapper).updateById(any(Account.class));
        verify(sessionService).revokeAllSessions(1L);
    }

    @Test
    void shouldRejectInvalidToken() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> recoveryService.completeRecovery("invalidToken", "newPassword"));
        assertEquals(ErrorCode.RECOVERY_NOT_AVAILABLE, e.getErrorCode());
    }

    @Test
    void shouldRejectReusedToken() {
        String token = recoveryService.initiateRecovery();

        // First use succeeds (but there's no admin account, so it would fail)
        // Instead, let's test that completing twice fails
        Account admin = new Account();
        admin.setId(1L);
        admin.setPasswordHash("oldHash");

        when(accountMapper.selectList(any())).thenReturn(List.of(admin));
        when(roleBindingMapper.findRolesByAccountId(1L)).thenReturn(List.of("INSTANCE_ADMIN"));
        when(accountMapper.findById(1L)).thenReturn(java.util.Optional.of(admin));
        when(accountMapper.updateById(any(Account.class))).thenReturn(1);

        // Complete once
        recoveryService.completeRecovery(token, "newPassword1");

        // Second completion should fail (token consumed)
        BusinessException e = assertThrows(BusinessException.class,
                () -> recoveryService.completeRecovery(token, "newPassword2"));
        assertEquals(ErrorCode.RECOVERY_NOT_AVAILABLE, e.getErrorCode());
    }
}
