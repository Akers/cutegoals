package com.cutegoals.family.service;

import com.cutegoals.auth.mapper.FamilyMemberMapper;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.auth.service.SessionService;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.family.DeviceBinding;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.mapper.ChildProfileMapper;
import com.cutegoals.family.mapper.DeviceBindingMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for device binding and child PIN login (Task 2.12).
 */
@Service
@RequiredArgsConstructor
public class DeviceBindingService {

    private static final Logger log = LoggerFactory.getLogger(DeviceBindingService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CREDENTIAL_BYTES = 32;
    private static final int PIN_LOCK_DURATION_MINUTES = 15;
    private static final int MAX_PIN_ATTEMPTS = 5;

    private final DeviceBindingMapper deviceBindingMapper;
    private final ChildProfileMapper childProfileMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder;

    // PIN failure tracking: key = "deviceId:childProfileId", value = [failureCount, lockedUntilEpoch]
    private final ConcurrentHashMap<String, long[]> pinFailureTracker = new ConcurrentHashMap<>();

    /**
     * Bind a device (parent authorizes).
     */
    @Transactional
    public Map<String, Object> bindDevice(Long familyId, String deviceId, Long accountId) {
        var existingBinding = deviceBindingMapper.findActiveByDeviceId(deviceId);
        if (existingBinding.isPresent()) {
            DeviceBinding binding = existingBinding.get();
            if (!binding.getFamilyId().equals(familyId)) {
                throw new BusinessException(ErrorCode.SINGLE_FAMILY_ONLY);
            }
            return buildBindingResponse(binding, null);
        }

        // Generate one-time credential (not logged)
        byte[] credentialBytes = new byte[CREDENTIAL_BYTES];
        RANDOM.nextBytes(credentialBytes);
        String plainCredential = HexFormat.of().formatHex(credentialBytes);
        String credentialHash = hashCredential(plainCredential);

        DeviceBinding binding = new DeviceBinding();
        binding.setFamilyId(familyId);
        binding.setDeviceId(deviceId);
        binding.setCredentialHash(credentialHash);
        binding.setStatus("ACTIVE");
        binding.setBoundBy(accountId);
        deviceBindingMapper.insert(binding);

        auditService.record(AuditEvent.DEVICE_BOUND, accountId, "SUCCESS",
                "Device bound: id=" + binding.getId() + ", deviceId=" + deviceId);
        log.info("Device bound: id={}, deviceId={}, familyId={}", binding.getId(), deviceId, familyId);

        return buildBindingResponse(binding, plainCredential);
    }

    /**
     * Revoke a device binding and all associated child sessions.
     */
    @Transactional
    public void revokeDeviceBinding(Long bindingId, Long familyId, Long accountId) {
        DeviceBinding binding = deviceBindingMapper.findById(bindingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!binding.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!"ACTIVE".equals(binding.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        deviceBindingMapper.revokeById(bindingId);

        // Revoke all child sessions associated with this device
        sessionService.revokeSessionsByDevice(binding.getDeviceId());

        auditService.record(AuditEvent.DEVICE_REVOKED, accountId, "SUCCESS",
                "Device binding revoked: id=" + bindingId + ", deviceId=" + binding.getDeviceId());
        log.info("Device binding revoked: id={}, deviceId={}", bindingId, binding.getDeviceId());
    }

    /**
     * Get child list for a device (pre-PIN login).
     */
    public List<Map<String, Object>> getChildrenForDevice(String deviceId) {
        DeviceBinding binding = deviceBindingMapper.findActiveByDeviceId(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_AUTHORIZED));

        List<ChildProfile> activeChildren = childProfileMapper
                .findActiveOnlyByFamilyId(binding.getFamilyId());

        return activeChildren.stream()
                .map(c -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("id", c.getId());
                    info.put("nickname", c.getNickname());
                    info.put("avatar", c.getAvatar());
                    info.put("hasPin", c.getPinHash() != null);
                    return info;
                })
                .collect(Collectors.toList());
    }

    /**
     * Child PIN login.
     */
    @Transactional
    public Map<String, Object> childLogin(String deviceId, Long childId, String pin) {
        // Validate device binding
        DeviceBinding binding = deviceBindingMapper.findActiveByDeviceId(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_AUTHORIZED));

        // Get child profile
        ChildProfile profile = childProfileMapper.findById(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHILD_AUTHENTICATION_FAILED));

        // Verify child belongs to the same family as the device binding
        if (!profile.getFamilyId().equals(binding.getFamilyId())) {
            throw new BusinessException(ErrorCode.CHILD_AUTHENTICATION_FAILED);
        }

        // Check child is active
        if (!"ACTIVE".equals(profile.getStatus())) {
            throw new BusinessException(ErrorCode.CHILD_AUTHENTICATION_FAILED);
        }

        // Check PIN is set
        if (profile.getPinHash() == null) {
            throw new BusinessException(ErrorCode.CHILD_AUTHENTICATION_FAILED);
        }

        // Check PIN lockout
        String lockKey = deviceId + ":" + childId;
        long[] failureState = pinFailureTracker.get(lockKey);
        if (failureState != null) {
            if (failureState[0] >= MAX_PIN_ATTEMPTS) {
                long lockedUntil = failureState[1];
                if (System.currentTimeMillis() < lockedUntil) {
                    throw new BusinessException(ErrorCode.PIN_LOCKED);
                } else {
                    pinFailureTracker.remove(lockKey);
                }
            }
        }

        // Verify PIN
        boolean pinMatches;
        try {
            pinMatches = passwordEncoder.matches(pin, profile.getPinHash());
        } catch (Exception e) {
            pinMatches = false;
        }

        if (!pinMatches) {
            long now = System.currentTimeMillis();
            pinFailureTracker.compute(lockKey, (key, state) -> {
                if (state == null) {
                    return new long[]{1, 0};
                }
                state[0]++;
                if (state[0] >= MAX_PIN_ATTEMPTS) {
                    state[1] = now + (PIN_LOCK_DURATION_MINUTES * 60_000L);
                }
                return state;
            });

            auditService.record(AuditEvent.CHILD_LOGIN_FAILED, null, "FAILED",
                    "Child PIN login failed: childId=" + childId + ", deviceId=" + deviceId);

            long[] updatedState = pinFailureTracker.get(lockKey);
            if (updatedState != null && updatedState[0] >= MAX_PIN_ATTEMPTS) {
                auditService.record(AuditEvent.PIN_LOCKED_EVENT, null, "FAILED",
                        "PIN locked: childId=" + childId + ", deviceId=" + deviceId);
                throw new BusinessException(ErrorCode.PIN_LOCKED);
            }

            throw new BusinessException(ErrorCode.CHILD_AUTHENTICATION_FAILED);
        }

        // Success: clear failure count
        pinFailureTracker.remove(lockKey);

        // Create child session
        String sessionId = sessionService.createChildSession(childId, deviceId);

        auditService.record(AuditEvent.CHILD_LOGIN_SUCCESS, null, "SUCCESS",
                "Child PIN login successful: childId=" + childId + ", deviceId=" + deviceId);
        log.info("Child login successful: childId={}, deviceId={}", childId, deviceId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("childId", childId);
        result.put("nickname", profile.getNickname());
        result.put("familyId", profile.getFamilyId());
        return result;
    }

    // === Helpers ===

    private Map<String, Object> buildBindingResponse(DeviceBinding binding, String plainCredential) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", binding.getId());
        result.put("deviceId", binding.getDeviceId());
        result.put("familyId", binding.getFamilyId());
        result.put("status", binding.getStatus());
        result.put("boundBy", binding.getBoundBy());
        result.put("createdAt", binding.getCreatedAt());
        if (plainCredential != null) {
            result.put("credential", plainCredential);
        }
        return result;
    }

    private static String hashCredential(String plain) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash credential", e);
        }
    }
}
