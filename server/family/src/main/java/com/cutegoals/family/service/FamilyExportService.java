package com.cutegoals.family.service;

import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.mapper.FamilyMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.entity.family.FamilyMember;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.mapper.ChildProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for family data export (Task 6.7).
 * Excludes secrets, anonymizes deleted children.
 */
@Service
@RequiredArgsConstructor
public class FamilyExportService {

    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final ChildProfileMapper childProfileMapper;

    /**
     * Export family data for the given account.
     * Only the PARENT role is authorized to call this.
     */
    public Map<String, Object> exportFamilyData(Long accountId) {
        // Find the family member for this account
        FamilyMember member = familyMemberMapper.findByAccountIdAndRole(accountId, "PARENT")
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN,
                        "Account is not a PARENT member"));

        Long familyId = member.getFamilyId();
        Family family = familyMapper.findById(familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Map<String, Object> result = new LinkedHashMap<>();

        // Family info (no secrets)
        result.put("familyName", family.getName());
        result.put("familyAvatar", family.getAvatar());
        result.put("createdAt", family.getCreatedAt());

        // Active parent members (no secrets)
        List<FamilyMember> activeParents = familyMemberMapper
                .findActiveByFamilyIdAndRole(familyId, "PARENT");
        List<Map<String, Object>> parentList = new ArrayList<>();
        for (FamilyMember pm : activeParents) {
            Map<String, Object> pmInfo = new LinkedHashMap<>();
            pmInfo.put("memberId", pm.getId());
            pmInfo.put("accountId", pm.getAccountId());
            pmInfo.put("status", pm.getStatus());
            pmInfo.put("joinedAt", pm.getCreatedAt());
            parentList.add(pmInfo);
        }
        result.put("parents", parentList);

        // Child profiles (no PIN hash, anonymize deleted)
        LambdaQueryWrapper<ChildProfile> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(ChildProfile::getFamilyId, familyId);
        List<ChildProfile> allChildren = childProfileMapper.selectList(childWrapper);
        List<Map<String, Object>> childrenList = new ArrayList<>();
        int deletedCounter = 1;
        for (ChildProfile child : allChildren) {
            Map<String, Object> childInfo = new LinkedHashMap<>();
            childInfo.put("id", child.getId());

            if ("DELETED".equals(child.getStatus())) {
                // Anonymize deleted children
                childInfo.put("nickname", "已删除孩子 " + deletedCounter);
                childInfo.put("avatar", null);
                childInfo.put("deleted", true);
                deletedCounter++;
            } else {
                childInfo.put("nickname", child.getNickname());
                childInfo.put("avatar", child.getAvatar());
                childInfo.put("deleted", false);
            }

            childInfo.put("status", child.getStatus());
            childInfo.put("hasPin", child.getPinHash() != null);
            childInfo.put("birthYear", child.getBirthYear());
            childInfo.put("ageGroup", child.getAgeGroup());
            childInfo.put("createdAt", child.getCreatedAt());

            // Explicitly exclude: pinHash, device binding secrets
            childrenList.add(childInfo);
        }
        result.put("children", childrenList);

        return result;
    }
}
