package com.cutegoals.family.service;

import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.points.PointsBalance;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.mapper.ChildProfileMapper;
import com.cutegoals.points.mapper.PointsBalanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChildProfileServiceTest {

    @Mock private ChildProfileMapper childProfileMapper;
    @Mock private PointsBalanceMapper pointsBalanceMapper;
    @Mock private AuditService auditService;

    private BCryptPasswordEncoder passwordEncoder;
    private ChildProfileService childProfileService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        childProfileService = new ChildProfileService(childProfileMapper, passwordEncoder, auditService, pointsBalanceMapper);
    }

    @Test
    void shouldCreateChildProfileWithPin() {
        when(childProfileMapper.findActiveByFamilyId(anyLong())).thenReturn(List.of());
        doReturn(1).when(childProfileMapper).insert(any(ChildProfile.class));

        Map<String, Object> result = childProfileService.createChildProfile(
                1L, 100L, "小明", "/avatar.png", "1234");

        assertEquals("小明", result.get("nickname"));
        assertEquals("/avatar.png", result.get("avatar"));
        assertTrue((Boolean) result.get("hasPin"));
        assertNull(result.get("pinHash"));
        verify(childProfileMapper).insert(any(ChildProfile.class));
        verify(pointsBalanceMapper).insert(any(PointsBalance.class));
        verify(auditService).record(eq("CHILD_CREATED"), eq(100L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldCreateChildProfileWithoutPin() {
        doReturn(1).when(childProfileMapper).insert(any(ChildProfile.class));

        Map<String, Object> result = childProfileService.createChildProfile(
                1L, 100L, "小红", null, null);

        assertEquals("小红", result.get("nickname"));
        assertFalse((Boolean) result.get("hasPin"));
        verify(childProfileMapper).insert(any(ChildProfile.class));
        verify(pointsBalanceMapper).insert(any(PointsBalance.class));
    }

    @Test
    void shouldRejectBlankNickname() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> childProfileService.createChildProfile(1L, 100L, "", null, null));
        assertEquals(ErrorCode.VALIDATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldRejectDuplicatePinInSameFamily() {
        String pin = "1234";
        String pinHash = passwordEncoder.encode(pin);

        ChildProfile existingChild = new ChildProfile();
        existingChild.setId(20L);
        existingChild.setFamilyId(1L);
        existingChild.setNickname("小红");
        existingChild.setPinHash(pinHash);
        existingChild.setStatus("ACTIVE");

        when(childProfileMapper.findActiveByFamilyId(1L)).thenReturn(List.of(existingChild));

        BusinessException e = assertThrows(BusinessException.class,
                () -> childProfileService.createChildProfile(1L, 100L, "小明", "/avatar.png", pin));
        assertEquals(ErrorCode.PIN_CONFLICT, e.getErrorCode());
        verify(childProfileMapper, never()).insert(any(ChildProfile.class));
    }

    @Test
    void shouldUpdateChildNickname() {
        ChildProfile profile = new ChildProfile();
        profile.setId(10L);
        profile.setFamilyId(1L);
        profile.setNickname("Old Name");
        profile.setStatus("ACTIVE");
        profile.setCreatedAt(LocalDateTime.now());

        when(childProfileMapper.findById(10L)).thenReturn(Optional.of(profile));
        doReturn(1).when(childProfileMapper).updateById(any(ChildProfile.class));

        Map<String, Object> result = childProfileService.updateChildProfile(
                10L, 1L, 100L, Map.of("nickname", "New Name"));

        assertEquals("New Name", result.get("nickname"));
        verify(auditService).record(eq("CHILD_UPDATED"), eq(100L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldRejectUpdateForWrongFamily() {
        ChildProfile profile = new ChildProfile();
        profile.setId(10L);
        profile.setFamilyId(2L);

        when(childProfileMapper.findById(10L)).thenReturn(Optional.of(profile));

        BusinessException e = assertThrows(BusinessException.class,
                () -> childProfileService.updateChildProfile(10L, 1L, 100L, Map.of("nickname", "X")));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void shouldRejectUpdateForDeletedChild() {
        ChildProfile profile = new ChildProfile();
        profile.setId(10L);
        profile.setFamilyId(1L);
        profile.setStatus("DELETED");

        when(childProfileMapper.findById(10L)).thenReturn(Optional.of(profile));

        BusinessException e = assertThrows(BusinessException.class,
                () -> childProfileService.updateChildProfile(10L, 1L, 100L, Map.of("nickname", "X")));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void shouldDeleteAndAnonymizeChildProfile() {
        ChildProfile profile = new ChildProfile();
        profile.setId(10L);
        profile.setFamilyId(1L);
        profile.setStatus("ACTIVE");

        when(childProfileMapper.findById(10L)).thenReturn(Optional.of(profile));

        childProfileService.deleteChildProfile(10L, 1L, 100L);

        verify(childProfileMapper).anonymizeDelete(10L);
        verify(auditService).record(eq("CHILD_DELETED"), eq(100L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldRejectDeleteAlreadyDeletedChild() {
        ChildProfile profile = new ChildProfile();
        profile.setId(10L);
        profile.setFamilyId(1L);
        profile.setStatus("DELETED");

        when(childProfileMapper.findById(10L)).thenReturn(Optional.of(profile));

        BusinessException e = assertThrows(BusinessException.class,
                () -> childProfileService.deleteChildProfile(10L, 1L, 100L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }
}
