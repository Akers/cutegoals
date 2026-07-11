package com.cutegoals.instancemanagement.service;

import com.cutegoals.auth.mapper.RoleBindingMapper;
import com.cutegoals.auth.mapper.SessionMapper;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.auth.Account;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.instancemanagement.mapper.AccountManagementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountManagementServiceTest {

    @Mock
    private AccountManagementMapper accountManagementMapper;
    @Mock
    private RoleBindingMapper roleBindingMapper;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private AuditService auditService;

    @Captor
    private ArgumentCaptor<String> eventTypeCaptor;
    @Captor
    private ArgumentCaptor<Long> actorIdCaptor;
    @Captor
    private ArgumentCaptor<String> resultCaptor;

    private AccountManagementService service;

    private Account activeAccount;
    private Account disabledAccount;

    @BeforeEach
    void setUp() {
        service = new AccountManagementService(
                accountManagementMapper, roleBindingMapper, sessionMapper, auditService);

        activeAccount = new Account();
        activeAccount.setId(1L);
        activeAccount.setPhone("13800138000");
        activeAccount.setStatus(AuthConstants.STATUS_ACTIVE);

        disabledAccount = new Account();
        disabledAccount.setId(2L);
        disabledAccount.setPhone("13800138001");
        disabledAccount.setStatus(AuthConstants.STATUS_DISABLED);
    }

    // === enableAccount ===

    @Test
    void shouldEnableAccount() {
        when(accountManagementMapper.findById(2L)).thenReturn(Optional.of(disabledAccount));

        service.enableAccount(2L, 99L);

        verify(accountManagementMapper).updateStatus(2L, AuthConstants.STATUS_ACTIVE);
        verify(auditService).record(eq(AuditEvent.ACCOUNT_ENABLED), eq(99L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldBeIdempotentWhenAlreadyActive() {
        when(accountManagementMapper.findById(1L)).thenReturn(Optional.of(activeAccount));

        service.enableAccount(1L, 99L);

        verify(accountManagementMapper, never()).updateStatus(anyLong(), anyString());
        verify(auditService, never()).record(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void shouldThrowNotFoundWhenEnablingNonExistentAccount() {
        when(accountManagementMapper.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.enableAccount(999L, 99L));
    }

    @Test
    void shouldRollbackOnAuditFailureDuringEnable() {
        when(accountManagementMapper.findById(2L)).thenReturn(Optional.of(disabledAccount));
        doThrow(new RuntimeException("DB write failed"))
                .when(auditService).record(anyString(), anyLong(), anyString(), anyString());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.enableAccount(2L, 99L));
        assertEquals(ErrorCode.AUDIT_UNAVAILABLE, ex.getErrorCode());
    }

    // === disableAccount ===

    @Test
    void shouldDisableAccount() {
        when(accountManagementMapper.findByIdWithLock(2L)).thenReturn(Optional.of(disabledAccount));

        // Account already disabled
        service.disableAccount(2L, 99L);

        verify(accountManagementMapper, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void shouldDisableActiveAccountWithoutAdminRole() {
        when(accountManagementMapper.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));
        when(roleBindingMapper.findRolesByAccountId(1L)).thenReturn(List.of(AuthConstants.ROLE_PARENT));
        when(accountManagementMapper.countActiveParentsWithLock()).thenReturn(2L);

        service.disableAccount(1L, 99L);

        verify(accountManagementMapper).updateStatus(1L, AuthConstants.STATUS_DISABLED);
        verify(sessionMapper).revokeAllByAccountId(1L);
        verify(auditService).record(eq(AuditEvent.ACCOUNT_DISABLED), eq(99L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldThrowLastInstanceAdmin() {
        when(accountManagementMapper.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));
        when(roleBindingMapper.findRolesByAccountId(1L)).thenReturn(List.of(AuthConstants.ROLE_INSTANCE_ADMIN));
        when(accountManagementMapper.countActiveInstanceAdminsWithLock()).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.disableAccount(1L, 99L));
        assertEquals(ErrorCode.LAST_INSTANCE_ADMIN, ex.getErrorCode());
        verify(accountManagementMapper, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void shouldThrowLastActiveParent() {
        when(accountManagementMapper.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));
        when(roleBindingMapper.findRolesByAccountId(1L)).thenReturn(List.of(AuthConstants.ROLE_PARENT));
        when(accountManagementMapper.countActiveParentsWithLock()).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.disableAccount(1L, 99L));
        assertEquals(ErrorCode.LAST_ACTIVE_PARENT, ex.getErrorCode());
        verify(accountManagementMapper, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void shouldThrowNotFoundWhenDisablingNonExistentAccount() {
        when(accountManagementMapper.findByIdWithLock(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.disableAccount(999L, 99L));
    }

    @Test
    void shouldRollbackOnAuditFailureDuringDisable() {
        when(accountManagementMapper.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));
        when(roleBindingMapper.findRolesByAccountId(1L)).thenReturn(List.of());
        doThrow(new RuntimeException("DB write failed"))
                .when(auditService).record(anyString(), anyLong(), anyString(), anyString());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.disableAccount(1L, 99L));
        assertEquals(ErrorCode.AUDIT_UNAVAILABLE, ex.getErrorCode());
        // Status update should not have happened (transaction rollback)
        verify(accountManagementMapper).updateStatus(1L, AuthConstants.STATUS_DISABLED);
    }

    // === getAccounts ===

    @Test
    void shouldReturnEmptyListForNoAccounts() {
        com.baomidou.mybatisplus.core.metadata.IPage<Account> emptyPage = mock();
        when(emptyPage.getRecords()).thenReturn(List.of());
        when(emptyPage.getCurrent()).thenReturn(1L);
        when(emptyPage.getSize()).thenReturn(20L);
        when(emptyPage.getTotal()).thenReturn(0L);
        when(emptyPage.getPages()).thenReturn(0L);
        when(accountManagementMapper.findAccountsWithPage(any())).thenReturn(emptyPage);

        var result = service.getAccounts(1, 20);

        assertEquals(0, ((List<?>) result.get("content")).size());
        assertEquals(0L, result.get("totalElements"));
    }
}
