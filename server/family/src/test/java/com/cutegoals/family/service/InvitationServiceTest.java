package com.cutegoals.family.service;

import com.cutegoals.auth.mapper.FamilyMemberMapper;
import com.cutegoals.auth.mapper.RoleBindingMapper;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.auth.RoleBinding;
import com.cutegoals.common.entity.family.FamilyMember;
import com.cutegoals.common.entity.family.ParentInvitation;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.mapper.ParentInvitationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock private ParentInvitationMapper parentInvitationMapper;
    @Mock private FamilyMemberMapper familyMemberMapper;
    @Mock private RoleBindingMapper roleBindingMapper;
    @Mock private AuditService auditService;

    private InvitationService invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(
                parentInvitationMapper, familyMemberMapper, roleBindingMapper, auditService);
    }

    @Test
    void shouldRejectRevokeFromWrongFamily() {
        ParentInvitation invitation = new ParentInvitation();
        invitation.setId(10L);
        invitation.setFamilyId(1L);
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(parentInvitationMapper.findById(10L)).thenReturn(Optional.of(invitation));

        BusinessException e = assertThrows(BusinessException.class,
                () -> invitationService.revokeInvitation(10L, 999L, 100L));
        assertEquals(ErrorCode.INVITATION_NOT_AVAILABLE, e.getErrorCode());
        verify(parentInvitationMapper, never()).updateStatusIfPending(anyLong(), anyString());
    }

    @Test
    void shouldCreateInvitationSuccessfully() {
        when(parentInvitationMapper.findByIdempotencyKey(any(), anyString()))
                .thenReturn(Optional.empty());
        when(parentInvitationMapper.findPendingByFamilyAndPhone(any(), anyString()))
                .thenReturn(Optional.empty());

        Map<String, Object> result = invitationService.createInvitation(
                1L, 100L, "13800138000", "key-1");

        assertNotNull(result.get("secret"));
        assertEquals("PENDING", result.get("status"));
        verify(parentInvitationMapper).insert(any(ParentInvitation.class));
        verify(auditService).record(eq("INVITATION_CREATED"), eq(100L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldReturnExistingOnIdempotencyKey() {
        ParentInvitation existing = new ParentInvitation();
        existing.setId(10L);
        existing.setFamilyId(1L);
        existing.setInviterId(100L);
        existing.setTargetPhone("13800138000");
        existing.setStatus("PENDING");
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(parentInvitationMapper.findByIdempotencyKey(100L, "key-1"))
                .thenReturn(Optional.of(existing));

        Map<String, Object> result = invitationService.createInvitation(
                1L, 100L, "13800138000", "key-1");

        assertEquals(10L, result.get("id"));
        assertNull(result.get("secret"));
        verify(parentInvitationMapper, never()).insert(any(ParentInvitation.class));
    }

    @Test
    void shouldAcceptInvitationSuccessfully() {
        ParentInvitation invitation = new ParentInvitation();
        invitation.setId(10L);
        invitation.setFamilyId(1L);
        invitation.setInviterId(100L);
        invitation.setTargetPhone("13800138000");
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(parentInvitationMapper.findById(10L)).thenReturn(Optional.of(invitation));
        when(parentInvitationMapper.updateStatusIfPending(10L, "ACCEPTED")).thenReturn(1);

        Map<String, Object> result = invitationService.acceptInvitation(10L, 200L, "13800138000");

        assertEquals("ACCEPTED", result.get("status"));
        verify(roleBindingMapper).insert(any(RoleBinding.class));
        verify(familyMemberMapper).insert(any(FamilyMember.class));
        verify(auditService).record(eq("INVITATION_ACCEPTED"), eq(200L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldRejectExpiredInvitation() {
        ParentInvitation invitation = new ParentInvitation();
        invitation.setId(10L);
        invitation.setStatus("PENDING");
        invitation.setTargetPhone("13800138000");
        invitation.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(parentInvitationMapper.findById(10L)).thenReturn(Optional.of(invitation));

        BusinessException e = assertThrows(BusinessException.class,
                () -> invitationService.acceptInvitation(10L, 200L, "13800138000"));
        assertEquals(ErrorCode.INVITATION_NOT_AVAILABLE, e.getErrorCode());
    }

    @Test
    void shouldRejectInvitationWithWrongPhone() {
        ParentInvitation invitation = new ParentInvitation();
        invitation.setId(10L);
        invitation.setTargetPhone("13800138000");
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(parentInvitationMapper.findById(10L)).thenReturn(Optional.of(invitation));

        BusinessException e = assertThrows(BusinessException.class,
                () -> invitationService.acceptInvitation(10L, 200L, "13900139000"));
        assertEquals(ErrorCode.INVITATION_NOT_AVAILABLE, e.getErrorCode());
    }

    @Test
    void shouldRejectInvitationSuccessfully() {
        ParentInvitation invitation = new ParentInvitation();
        invitation.setId(10L);
        invitation.setTargetPhone("13800138000");
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(parentInvitationMapper.findById(10L)).thenReturn(Optional.of(invitation));
        when(parentInvitationMapper.updateStatusIfPending(10L, "REJECTED")).thenReturn(1);

        Map<String, Object> result = invitationService.rejectInvitation(10L, 200L, "13800138000");
        assertEquals("REJECTED", result.get("status"));
        verify(auditService).record(eq("INVITATION_REJECTED"), eq(200L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldRevokeInvitationSuccessfully() {
        ParentInvitation invitation = new ParentInvitation();
        invitation.setId(10L);
        invitation.setFamilyId(1L);
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(parentInvitationMapper.findById(10L)).thenReturn(Optional.of(invitation));
        when(parentInvitationMapper.updateStatusIfPending(10L, "REVOKED")).thenReturn(1);

        Map<String, Object> result = invitationService.revokeInvitation(10L, 1L, 100L);
        assertEquals("REVOKED", result.get("status"));
        verify(auditService).record(eq("INVITATION_REVOKED"), eq(100L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldReturnNotAvailableForAlreadyUsedInvitation() {
        ParentInvitation invitation = new ParentInvitation();
        invitation.setId(10L);
        invitation.setStatus("ACCEPTED");
        invitation.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(parentInvitationMapper.findById(10L)).thenReturn(Optional.of(invitation));

        BusinessException e = assertThrows(BusinessException.class,
                () -> invitationService.acceptInvitation(10L, 200L, "13800138000"));
        assertEquals(ErrorCode.INVITATION_NOT_AVAILABLE, e.getErrorCode());
    }

    @Test
    void shouldHandleConcurrentAcceptWithAtomicUpdate() {
        ParentInvitation invitation = new ParentInvitation();
        invitation.setId(10L);
        invitation.setFamilyId(1L);
        invitation.setTargetPhone("13800138000");
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(parentInvitationMapper.findById(10L)).thenReturn(Optional.of(invitation));
        when(parentInvitationMapper.updateStatusIfPending(10L, "ACCEPTED")).thenReturn(0);

        BusinessException e = assertThrows(BusinessException.class,
                () -> invitationService.acceptInvitation(10L, 200L, "13800138000"));
        assertEquals(ErrorCode.INVITATION_NOT_AVAILABLE, e.getErrorCode());
    }
}
