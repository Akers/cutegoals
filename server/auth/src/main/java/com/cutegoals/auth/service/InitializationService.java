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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

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

        // 8. 写入默认 instance_config（已存在则跳过）
        String insertConfigSql = """
                INSERT INTO instance_config (config_key, config_value, masked, description)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (config_key) DO NOTHING
                """;
        int inserted = jdbcTemplate.update(insertConfigSql, "sms.provider", "aliyun", false, "SMS provider (aliyun or none)");
        inserted += jdbcTemplate.update(insertConfigSql, "sms.enabled", "false", false, "Enable SMS login");
        inserted += jdbcTemplate.update(insertConfigSql, "recovery.email", "admin@cutegoals.local", false, "Recovery email for instance admin");
        inserted += jdbcTemplate.update(insertConfigSql, "backup.schedule", "0 2 * * *", false, "Backup cron schedule");
        inserted += jdbcTemplate.update(insertConfigSql, "backup.retention_days", "30", false, "Backup retention days");
        inserted += jdbcTemplate.update(insertConfigSql, "rate_limit.login_max", "5", false, "Max login attempts per window");
        inserted += jdbcTemplate.update(insertConfigSql, "rate_limit.pin_max", "3", false, "Max PIN attempts per window");
        if (inserted > 0) {
            log.info("Default instance_config rows inserted: {}", inserted);
        }

        log.info("Instance initialized: accountId={}, familyId={}", accountId, familyId);
        return accountId;
    }
}
