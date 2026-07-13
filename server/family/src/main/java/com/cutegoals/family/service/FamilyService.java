package com.cutegoals.family.service;

import com.cutegoals.auth.mapper.AccountMapper;
import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.mapper.FamilyMemberMapper;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.auth.service.SessionService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.entity.family.FamilyMember;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.mapper.ChildProfileMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for family management (Tasks 2.8, 2.11).
 */
@Service
@RequiredArgsConstructor
public class FamilyService {

    private static final Logger log = LoggerFactory.getLogger(FamilyService.class);

    private final AccountMapper accountMapper;
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final ChildProfileMapper childProfileMapper;
    private final AuditService auditService;
    private final SessionService sessionService;

    /** Allowed fields for family update (whitelist). */
    private static final Set<String> ALLOWED_UPDATE_FIELDS = Set.of("name", "avatar");

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_AVATAR_LENGTH = 500;
    private static final Pattern AVATAR_PATTERN = Pattern.compile("^(https?://|/).{0,498}$");

    /**
     * Get the current family for the given family ID.
     * Returns a sanitized response without secrets.
     */
    public Map<String, Object> getFamily(Long familyId) {
        Family family = familyMapper.findById(familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<FamilyMember> activeMembers = familyMemberMapper.findActiveByFamilyId(familyId);
        List<ChildProfile> activeChildren = childProfileMapper.findActiveByFamilyId(familyId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", family.getId());
        result.put("name", family.getName());
        result.put("avatar", family.getAvatar());
        result.put("createdAt", family.getCreatedAt());

        // Members: return only non-sensitive info
        List<Map<String, Object>> memberList = new ArrayList<>();
        for (FamilyMember m : activeMembers) {
            Map<String, Object> memberInfo = new LinkedHashMap<>();
            memberInfo.put("id", m.getId());
            memberInfo.put("accountId", m.getAccountId());
            memberInfo.put("role", m.getRole());
            memberInfo.put("status", m.getStatus());
            accountMapper.findById(m.getAccountId()).ifPresent(account -> memberInfo.put("phone", account.getPhone()));
            memberList.add(memberInfo);
        }
        result.put("members", memberList);

        // Children: return minimal info (no PIN hash)
        List<Map<String, Object>> childrenList = new ArrayList<>();
        for (ChildProfile c : activeChildren) {
            Map<String, Object> childInfo = new LinkedHashMap<>();
            childInfo.put("id", c.getId());
            childInfo.put("nickname", c.getNickname());
            childInfo.put("avatar", c.getAvatar());
            childInfo.put("status", c.getStatus());
            childInfo.put("hasPin", c.getPinHash() != null);
            childInfo.put("birthYear", c.getBirthYear());
            childInfo.put("ageGroup", c.getAgeGroup());
            childrenList.add(childInfo);
        }
        result.put("children", childrenList);

        return result;
    }

    /**
     * Update family information with whitelist validation.
     */
    @Transactional
    public Map<String, Object> updateFamily(Long familyId, Map<String, Object> updates, Long accountId) {
        Family family = familyMapper.findById(familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // Validate that only allowed fields are present
        for (String key : updates.keySet()) {
            if (!ALLOWED_UPDATE_FIELDS.contains(key)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Field '" + key + "' is not allowed for update");
            }
        }

        // Validate and apply each field
        if (updates.containsKey("name")) {
            Object nameVal = updates.get("name");
            if (nameVal == null || !(nameVal instanceof String s) || s.isBlank() || s.length() > MAX_NAME_LENGTH) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Family name must be non-blank and at most " + MAX_NAME_LENGTH + " characters");
            }
            family.setName(s.trim());
        }

        if (updates.containsKey("avatar")) {
            Object avatarVal = updates.get("avatar");
            if (avatarVal != null && (!(avatarVal instanceof String s) || s.length() > MAX_AVATAR_LENGTH
                    || !AVATAR_PATTERN.matcher(s).matches())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Family avatar must be a valid URL or path");
            }
            family.setAvatar(avatarVal != null ? ((String) avatarVal).trim() : null);
        }

        familyMapper.updateById(family);

        auditService.record(AuditEvent.FAMILY_UPDATED, accountId, "SUCCESS",
                "Family updated: id=" + familyId + ", fields=" + updates.keySet());

        log.info("Family updated: id={}", familyId);

        return getFamily(familyId);
    }

    // ========== Task 2.11: Member Removal & Parent Protection ==========

    /**
     * Remove a parent member with atomic last-active-parent protection.
     * Uses database-level conditional update to prevent TOCTOU race conditions.
     */
    @Transactional
    public void removeMember(Long memberId, Long familyId, Long callerAccountId) {
        var member = familyMemberMapper.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!member.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (!AuthConstants.ROLE_PARENT.equals(member.getRole())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Can only remove PARENT members");
        }

        // Atomic check-and-update at database level
        int updated = familyMemberMapper.tryDeactivateMember(memberId, familyId, AuthConstants.MEMBER_INACTIVE);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.LAST_ACTIVE_PARENT);
        }

        sessionService.revokeAllSessions(member.getAccountId());

        auditService.record(AuditEvent.MEMBER_REMOVED, callerAccountId, "SUCCESS",
                "Member removed: memberId=" + memberId + ", accountId=" + member.getAccountId());
        log.info("Member removed: memberId={}, accountId={}, by accountId={}", memberId, member.getAccountId(), callerAccountId);
    }

    /**
     * Current parent leaves the family with atomic last-active-parent protection.
     */
    @Transactional
    public void leaveFamily(Long accountId, Long familyId) {
        var member = familyMemberMapper.findByAccountIdAndRole(accountId, AuthConstants.ROLE_PARENT)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!member.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // Atomic check-and-update at database level
        int updated = familyMemberMapper.tryDeactivateMember(member.getId(), familyId, AuthConstants.MEMBER_INACTIVE);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.LAST_ACTIVE_PARENT);
        }

        sessionService.revokeAllSessions(accountId);

        auditService.record(AuditEvent.MEMBER_LEFT, accountId, "SUCCESS",
                "Member left family: memberId=" + member.getId() + ", accountId=" + accountId);
        log.info("Member left family: memberId={}, accountId={}", member.getId(), accountId);
    }
}
