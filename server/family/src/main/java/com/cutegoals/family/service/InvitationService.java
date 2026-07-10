package com.cutegoals.family.service;

import com.cutegoals.auth.mapper.FamilyMemberMapper;
import com.cutegoals.auth.mapper.RoleBindingMapper;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.auth.RoleBinding;
import com.cutegoals.common.entity.family.FamilyMember;
import com.cutegoals.common.entity.family.ParentInvitation;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.mapper.ParentInvitationMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for parent invitation management (Task 2.9).
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int INVITATION_SECRET_BYTES = 32;
    private static final int DEFAULT_VALIDITY_HOURS = 48;

    private final ParentInvitationMapper parentInvitationMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final RoleBindingMapper roleBindingMapper;
    private final AuditService auditService;

    /**
     * Create a parent invitation.
     */
    @Transactional
    public Map<String, Object> createInvitation(Long familyId, Long inviterId,
                                                 String targetPhone, String idempotencyKey) {
        // Check idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<ParentInvitation> existing = parentInvitationMapper.findByIdempotencyKey(inviterId, idempotencyKey);
            if (existing.isPresent()) {
                ParentInvitation inv = existing.get();
                if ("PENDING".equals(inv.getStatus()) && inv.getExpiresAt().isAfter(LocalDateTime.now())) {
                    return buildInvitationResponse(inv);
                }
                return buildInvitationResponse(inv);
            }
        }

        // Check for existing pending invitation for the same phone
        Optional<ParentInvitation> existingPending = parentInvitationMapper
                .findPendingByFamilyAndPhone(familyId, targetPhone);
        if (existingPending.isPresent()) {
            return buildInvitationResponse(existingPending.get());
        }

        // Generate invitation secret (not logged)
        byte[] secretBytes = new byte[INVITATION_SECRET_BYTES];
        RANDOM.nextBytes(secretBytes);
        String plainSecret = HexFormat.of().formatHex(secretBytes);
        String secretHash = hashSecret(plainSecret);

        // Create invitation
        ParentInvitation invitation = new ParentInvitation();
        invitation.setFamilyId(familyId);
        invitation.setInviterId(inviterId);
        invitation.setTargetPhone(targetPhone);
        invitation.setSecretHash(secretHash);
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(LocalDateTime.now().plusHours(DEFAULT_VALIDITY_HOURS));
        invitation.setIdempotencyKey(idempotencyKey);
        parentInvitationMapper.insert(invitation);

        auditService.record(AuditEvent.INVITATION_CREATED, inviterId, "SUCCESS",
                "Invitation created: id=" + invitation.getId() + ", target=" + maskPhone(targetPhone));

        log.info("Invitation created: id={}, inviterId={}", invitation.getId(), inviterId);

        Map<String, Object> result = buildInvitationResponse(invitation);
        // Return the secret only on creation (one-time)
        result.put("secret", plainSecret);
        return result;
    }

    /**
     * Accept an invitation. Atomically creates PARENT role + family member.
     */
    @Transactional
    public Map<String, Object> acceptInvitation(Long invitationId, Long accountId, String accountPhone) {
        ParentInvitation invitation = parentInvitationMapper.findById(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE));

        if (!"PENDING".equals(invitation.getStatus()) || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE);
        }

        if (!invitation.getTargetPhone().equals(accountPhone)) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE);
        }

        // Atomic: update status first (concurrency guard)
        int updated = parentInvitationMapper.updateStatusIfPending(invitationId, "ACCEPTED");
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE);
        }
        invitation.setStatus("ACCEPTED");

        // Grant PARENT role (idempotent via unique constraint)
        try {
            RoleBinding roleBinding = new RoleBinding();
            roleBinding.setAccountId(accountId);
            roleBinding.setRole(AuthConstants.ROLE_PARENT);
            roleBindingMapper.insert(roleBinding);
        } catch (DuplicateKeyException e) {
            log.warn("PARENT role already exists for accountId={}: {}", accountId, e.getMessage());
        }

        // Create family member (idempotent via unique constraint)
        try {
            FamilyMember member = new FamilyMember();
            member.setFamilyId(invitation.getFamilyId());
            member.setAccountId(accountId);
            member.setRole(AuthConstants.ROLE_PARENT);
            member.setStatus(AuthConstants.MEMBER_ACTIVE);
            familyMemberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            log.warn("Family member already exists for accountId={}: {}", accountId, e.getMessage());
        }

        auditService.record(AuditEvent.INVITATION_ACCEPTED, accountId, "SUCCESS",
                "Invitation accepted: id=" + invitationId + ", accountId=" + accountId);
        log.info("Invitation accepted: id={}, accountId={}", invitationId, accountId);

        return buildInvitationResponse(invitation);
    }

    /**
     * Reject an invitation.
     */
    @Transactional
    public Map<String, Object> rejectInvitation(Long invitationId, Long accountId, String accountPhone) {
        ParentInvitation invitation = parentInvitationMapper.findById(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE));

        if (!"PENDING".equals(invitation.getStatus()) || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE);
        }

        if (!invitation.getTargetPhone().equals(accountPhone)) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE);
        }

        int updated = parentInvitationMapper.updateStatusIfPending(invitationId, "REJECTED");
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE);
        }

        invitation.setStatus("REJECTED");

        auditService.record(AuditEvent.INVITATION_REJECTED, accountId, "SUCCESS",
                "Invitation rejected: id=" + invitationId);
        log.info("Invitation rejected: id={}", invitationId);

        return buildInvitationResponse(invitation);
    }

    /**
     * Revoke an invitation (by inviter or any valid parent in the same family).
     */
    @Transactional
    public Map<String, Object> revokeInvitation(Long invitationId, Long callerFamilyId, Long accountId) {
        ParentInvitation invitation = parentInvitationMapper.findById(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE));

        // Verify the caller belongs to the invitation's family
        if (!invitation.getFamilyId().equals(callerFamilyId)) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE);
        }

        if (!"PENDING".equals(invitation.getStatus()) || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE);
        }

        int updated = parentInvitationMapper.updateStatusIfPending(invitationId, "REVOKED");
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_AVAILABLE);
        }

        invitation.setStatus("REVOKED");

        auditService.record(AuditEvent.INVITATION_REVOKED, accountId, "SUCCESS",
                "Invitation revoked: id=" + invitationId);
        log.info("Invitation revoked: id={}, by accountId={}", invitationId, accountId);

        return buildInvitationResponse(invitation);
    }

    // === Helpers ===

    private Map<String, Object> buildInvitationResponse(ParentInvitation invitation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", invitation.getId());
        result.put("familyId", invitation.getFamilyId());
        result.put("inviterId", invitation.getInviterId());
        result.put("targetPhone", maskPhone(invitation.getTargetPhone()));
        result.put("status", invitation.getStatus());
        result.put("expiresAt", invitation.getExpiresAt());
        result.put("createdAt", invitation.getCreatedAt());
        return result;
    }

    private static String hashSecret(String plainSecret) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash invitation secret", e);
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
