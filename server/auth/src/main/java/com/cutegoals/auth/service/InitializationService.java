package com.cutegoals.auth.service;

import com.cutegoals.auth.mapper.AccountMapper;
import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.mapper.FamilyMemberMapper;
import com.cutegoals.auth.mapper.RoleBindingMapper;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.auth.Account;
import com.cutegoals.common.entity.auth.RoleBinding;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.entity.family.FamilyMember;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end instance initialization: consume token → create account → create family → add member.
 * All operations happen in a single database transaction.
 */
@Service
@RequiredArgsConstructor
public class InitializationService {

    private static final Logger log = LoggerFactory.getLogger(InitializationService.class);

    private final InitializationTokenService tokenService;
    private final AccountMapper accountMapper;
    private final RoleBindingMapper roleBindingMapper;
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * Perform full instance initialization in a single transaction.
     *
     * @param plainToken the initialization token
     * @param phone      admin phone number
     * @param password   admin password
     * @return the created account ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long initialize(String plainToken, String phone, String password) {
        // 1. Validate and consume the token
        tokenService.consumeToken(plainToken);

        // 2. Normalize phone
        String normalizedPhone = PhoneNormalizer.normalize(phone);

        // 3. Create account with dual roles
        Account account = new Account();
        account.setPhone(normalizedPhone);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setStatus(AuthConstants.STATUS_ACTIVE);
        accountMapper.insert(account);

        Long accountId = account.getId();

        // 4. Grant INSTANCE_ADMIN role
        RoleBinding adminRole = new RoleBinding();
        adminRole.setAccountId(accountId);
        adminRole.setRole(AuthConstants.ROLE_INSTANCE_ADMIN);
        roleBindingMapper.insert(adminRole);

        // 5. Grant PARENT role
        RoleBinding parentRole = new RoleBinding();
        parentRole.setAccountId(accountId);
        parentRole.setRole(AuthConstants.ROLE_PARENT);
        roleBindingMapper.insert(parentRole);

        // 6. Create the unique family
        Family family = new Family();
        family.setName("我的家庭");
        familyMapper.insert(family);

        Long familyId = family.getId();

        // 7. Add account as family member (PARENT role in family)
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setAccountId(accountId);
        member.setRole(AuthConstants.ROLE_PARENT);
        member.setStatus(AuthConstants.MEMBER_ACTIVE);
        familyMemberMapper.insert(member);

        auditService.record(AuditEvent.INITIALIZE, accountId, "SUCCESS",
                "Instance initialized: accountId=" + accountId + ", familyId=" + familyId);
        log.info("Instance initialized: accountId={}, familyId={}", accountId, familyId);
        return accountId;
    }
}
