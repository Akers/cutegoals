package com.cutegoals.instancemanagement.service;

import com.cutegoals.auth.mapper.RoleBindingMapper;
import com.cutegoals.auth.mapper.SessionMapper;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.auth.Account;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.common.util.MaskUtil;
import com.cutegoals.instancemanagement.mapper.AccountManagementMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for account list and enable/disable management (Task 6.3).
 */
@Service
@RequiredArgsConstructor
public class AccountManagementService {

    private static final Logger log = LoggerFactory.getLogger(AccountManagementService.class);

    private final AccountManagementMapper accountManagementMapper;
    private final RoleBindingMapper roleBindingMapper;
    private final SessionMapper sessionMapper;
    private final AuditService auditService;

    /**
     * Get paginated account list with masked phone numbers.
     */
    public Map<String, Object> getAccounts(int pageNum, int pageSize) {
        List<Account> allAccounts = accountManagementMapper.findAllAccounts();

        // Simple in-memory pagination
        int total = allAccounts.size();
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        if (fromIndex >= total) {
            fromIndex = Math.max(0, total - pageSize);
            toIndex = total;
        }
        if (fromIndex < 0) fromIndex = 0;

        List<Account> pageAccounts = allAccounts.subList(fromIndex, toIndex);

        List<Map<String, Object>> accountList = new ArrayList<>();
        for (Account account : pageAccounts) {
            List<String> roles = roleBindingMapper.findRolesByAccountId(account.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", account.getId());
            item.put("phone", MaskUtil.maskPhone(account.getPhone()));
            item.put("status", account.getStatus());
            item.put("roles", roles);
            item.put("createdAt", account.getCreatedAt());
            item.put("updatedAt", account.getUpdatedAt());
            // Exclude passwordHash and other secrets
            accountList.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", accountList);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        result.put("totalElements", total);
        result.put("totalPages", (int) Math.ceil((double) total / pageSize));
        return result;
    }

    /**
     * Enable an account.
     */
    @Transactional
    public void enableAccount(Long accountId, Long adminAccountId) {
        Account account = accountManagementMapper.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (AuthConstants.STATUS_ACTIVE.equals(account.getStatus())) {
            return; // Already active, idempotent
        }

        accountManagementMapper.updateStatus(accountId, AuthConstants.STATUS_ACTIVE);

        auditService.record(AuditEvent.ACCOUNT_ENABLED, adminAccountId, "SUCCESS",
                "Account enabled: accountId=" + accountId);
        log.info("Account enabled: accountId={}, by adminAccountId={}", accountId, adminAccountId);
    }

    /**
     * Disable an account with LAST_INSTANCE_ADMIN and LAST_ACTIVE_PARENT protection.
     * Revokes all sessions atomically.
     */
    @Transactional
    public void disableAccount(Long accountId, Long adminAccountId) {
        Account account = accountManagementMapper.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!AuthConstants.STATUS_ACTIVE.equals(account.getStatus())) {
            return; // Already inactive/disabled, idempotent
        }

        List<String> roles = roleBindingMapper.findRolesByAccountId(accountId);

        // Protect last INSTANCE_ADMIN
        if (roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            long activeAdminCount = accountManagementMapper.countActiveInstanceAdmins();
            if (activeAdminCount <= 1) {
                throw new BusinessException(ErrorCode.LAST_INSTANCE_ADMIN,
                        "Cannot disable the last active INSTANCE_ADMIN");
            }
        }

        // Protect last active PARENT
        if (roles.contains(AuthConstants.ROLE_PARENT)) {
            long activeParentCount = accountManagementMapper.countActiveParents();
            if (activeParentCount <= 1) {
                throw new BusinessException(ErrorCode.LAST_ACTIVE_PARENT,
                        "Cannot disable the last active PARENT");
            }
        }

        // Change status to DISABLED
        accountManagementMapper.updateStatus(accountId, AuthConstants.STATUS_DISABLED);

        // Revoke all sessions immediately
        sessionMapper.revokeAllByAccountId(accountId);

        auditService.record(AuditEvent.ACCOUNT_DISABLED, adminAccountId, "SUCCESS",
                "Account disabled: accountId=" + accountId);
        log.info("Account disabled: accountId={}, by adminAccountId={}", accountId, adminAccountId);
    }
}
