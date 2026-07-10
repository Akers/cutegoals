package com.cutegoals.family.service;

import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.mapper.ChildProfileMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for child profile management (Task 2.10).
 */
@Service
@RequiredArgsConstructor
public class ChildProfileService {

    private static final Logger log = LoggerFactory.getLogger(ChildProfileService.class);

    private final ChildProfileMapper childProfileMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final int MAX_NICKNAME_LENGTH = 100;
    private static final int MAX_AVATAR_LENGTH = 500;

    /**
     * Create a child profile.
     */
    @Transactional
    public Map<String, Object> createChildProfile(Long familyId, Long accountId,
                                                    String nickname, String avatar,
                                                    String pin) {
        // Validate nickname
        if (nickname == null || nickname.isBlank() || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Nickname must be non-blank and at most " + MAX_NICKNAME_LENGTH + " characters");
        }

        // Validate and hash PIN
        String pinHash = null;
        if (pin != null && !pin.isBlank()) {
            validatePinUniqueness(familyId, pin);
            pinHash = passwordEncoder.encode(pin);
        }

        // Validate avatar if provided
        if (avatar != null && avatar.length() > MAX_AVATAR_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Avatar URL must be at most " + MAX_AVATAR_LENGTH + " characters");
        }

        ChildProfile profile = new ChildProfile();
        profile.setFamilyId(familyId);
        profile.setNickname(nickname.trim());
        profile.setAvatar(avatar != null ? avatar.trim() : null);
        profile.setPinHash(pinHash);
        profile.setStatus(AuthConstants.STATUS_ACTIVE);
        childProfileMapper.insert(profile);

        auditService.record(AuditEvent.CHILD_CREATED, accountId, "SUCCESS",
                "Child profile created: id=" + profile.getId() + ", nickname=" + nickname);

        log.info("Child profile created: id={}, familyId={}", profile.getId(), familyId);

        return buildChildResponse(profile);
    }

    /**
     * Update a child profile.
     */
    @Transactional
    public Map<String, Object> updateChildProfile(Long childId, Long familyId, Long accountId,
                                                    Map<String, Object> updates) {
        ChildProfile profile = childProfileMapper.findById(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!profile.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if ("DELETED".equals(profile.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // Validate and apply updates
        boolean pinChanged = false;
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            switch (entry.getKey()) {
                case "nickname" -> {
                    String val = (String) entry.getValue();
                    if (val == null || val.isBlank() || val.length() > MAX_NICKNAME_LENGTH) {
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                                "Nickname must be non-blank and at most " + MAX_NICKNAME_LENGTH + " characters");
                    }
                    profile.setNickname(val.trim());
                }
                case "avatar" -> {
                    String val = (String) entry.getValue();
                    if (val != null && val.length() > MAX_AVATAR_LENGTH) {
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                                "Avatar URL must be at most " + MAX_AVATAR_LENGTH + " characters");
                    }
                    profile.setAvatar(val != null ? val.trim() : null);
                }
                case "pin" -> {
                    String val = (String) entry.getValue();
                    if (val == null || val.isBlank()) {
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "PIN cannot be empty");
                    }
                    validatePinUniqueness(familyId, val);
                    profile.setPinHash(passwordEncoder.encode(val));
                    pinChanged = true;
                }
                case "birthYear" -> {
                    if (entry.getValue() != null) {
                        profile.setBirthYear(((Number) entry.getValue()).intValue());
                    } else {
                        profile.setBirthYear(null);
                    }
                }
                case "ageGroup" -> {
                    if (entry.getValue() != null) {
                        profile.setAgeGroup((String) entry.getValue());
                    } else {
                        profile.setAgeGroup(null);
                    }
                }
                case "status" -> {
                    String val = (String) entry.getValue();
                    if ("ACTIVE".equals(val) || "INACTIVE".equals(val)) {
                        profile.setStatus(val);
                    } else {
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid status: " + val);
                    }
                }
                default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Field '" + entry.getKey() + "' is not allowed for update");
            }
        }

        childProfileMapper.updateById(profile);

        String auditEvent = pinChanged ? AuditEvent.PIN_RESET : AuditEvent.CHILD_UPDATED;
        auditService.record(auditEvent, accountId, "SUCCESS",
                "Child profile updated: id=" + childId + ", fields=" + updates.keySet());

        log.info("Child profile updated: id={}", childId);

        return buildChildResponse(profile);
    }

    /**
     * Delete (anonymize) a child profile.
     */
    @Transactional
    public void deleteChildProfile(Long childId, Long familyId, Long accountId) {
        ChildProfile profile = childProfileMapper.findById(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!profile.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if ("DELETED".equals(profile.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // Anonymize: replace nickname, clear avatar, pin, optional fields
        childProfileMapper.anonymizeDelete(childId);

        auditService.record(AuditEvent.CHILD_DELETED, accountId, "SUCCESS",
                "Child profile deleted and anonymized: id=" + childId);

        log.info("Child profile deleted and anonymized: id={}", childId);
    }

    // === Helpers ===

    private void validatePinUniqueness(Long familyId, String pin) {
        List<ChildProfile> profiles = childProfileMapper.findActiveByFamilyId(familyId);
        for (ChildProfile profile : profiles) {
            if (profile.getPinHash() != null && passwordEncoder.matches(pin, profile.getPinHash())) {
                throw new BusinessException(ErrorCode.PIN_CONFLICT, "PIN already used by another child in this family");
            }
        }
    }

    private Map<String, Object> buildChildResponse(ChildProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", profile.getId());
        result.put("familyId", profile.getFamilyId());
        result.put("nickname", profile.getNickname());
        result.put("avatar", profile.getAvatar());
        result.put("status", profile.getStatus());
        result.put("hasPin", profile.getPinHash() != null);
        result.put("birthYear", profile.getBirthYear());
        result.put("ageGroup", profile.getAgeGroup());
        result.put("createdAt", profile.getCreatedAt());
        result.put("updatedAt", profile.getUpdatedAt());
        // Do NOT include pinHash
        return result;
    }
}
