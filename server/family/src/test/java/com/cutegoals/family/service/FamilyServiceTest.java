package com.cutegoals.family.service;

import com.cutegoals.auth.mapper.AccountMapper;
import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.mapper.FamilyMemberMapper;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.auth.service.SessionService;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.entity.family.FamilyMember;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.mapper.ChildProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock private AccountMapper accountMapper;
    @Mock private FamilyMapper familyMapper;
    @Mock private FamilyMemberMapper familyMemberMapper;
    @Mock private ChildProfileMapper childProfileMapper;
    @Mock private AuditService auditService;
    @Mock private SessionService sessionService;

    private FamilyService familyService;

    @BeforeEach
    void setUp() {
        familyService = new FamilyService(accountMapper, familyMapper, familyMemberMapper, childProfileMapper, auditService, sessionService);
    }

    @Test
    void shouldGetFamilySuccessfully() {
        Long familyId = 1L;
        Family family = new Family();
        family.setId(familyId);
        family.setName("Test Family");
        family.setAvatar("/avatar.png");
        family.setCreatedAt(LocalDateTime.now());

        FamilyMember member = new FamilyMember();
        member.setId(10L);
        member.setFamilyId(familyId);
        member.setAccountId(100L);
        member.setRole("PARENT");
        member.setStatus("ACTIVE");

        ChildProfile child = new ChildProfile();
        child.setId(20L);
        child.setFamilyId(familyId);
        child.setNickname("Kid");
        child.setAvatar("/kid.png");
        child.setStatus("ACTIVE");
        child.setPinHash("$2a$10$somehash");

        when(familyMapper.findById(familyId)).thenReturn(Optional.of(family));
        when(familyMemberMapper.findActiveByFamilyId(familyId)).thenReturn(List.of(member));
        when(childProfileMapper.findActiveByFamilyId(familyId)).thenReturn(List.of(child));

        Map<String, Object> result = familyService.getFamily(familyId);

        assertEquals("Test Family", result.get("name"));
        assertEquals("/avatar.png", result.get("avatar"));
        assertNotNull(result.get("members"));
        assertNotNull(result.get("children"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) result.get("children");
        assertEquals(1, children.size());
        assertTrue((Boolean) children.get(0).get("hasPin"));
        assertNull(children.get(0).get("pinHash"));
    }

    @Test
    void shouldReturnResourceNotFoundForNonExistentFamily() {
        when(familyMapper.findById(anyLong())).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> familyService.getFamily(999L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void shouldUpdateFamilyNameSuccessfully() {
        Long familyId = 1L;
        Family family = new Family();
        family.setId(familyId);
        family.setName("Old Name");

        when(familyMapper.findById(familyId)).thenReturn(Optional.of(family));
        doReturn(1).when(familyMapper).updateById(any(Family.class));
        when(familyMemberMapper.findActiveByFamilyId(anyLong())).thenReturn(List.of());
        when(childProfileMapper.findActiveByFamilyId(anyLong())).thenReturn(List.of());

        Map<String, Object> updates = Map.of("name", "New Family Name");
        Map<String, Object> result = familyService.updateFamily(familyId, updates, 100L);

        assertEquals("New Family Name", result.get("name"));
        verify(auditService).record(eq("FAMILY_UPDATED"), eq(100L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldRejectDisallowedField() {
        when(familyMapper.findById(anyLong())).thenReturn(Optional.of(new Family()));

        Map<String, Object> updates = Map.of("pin", "secret123");
        BusinessException e = assertThrows(BusinessException.class,
                () -> familyService.updateFamily(1L, updates, 100L));
        assertEquals(ErrorCode.VALIDATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldRejectBlankName() {
        when(familyMapper.findById(anyLong())).thenReturn(Optional.of(new Family()));

        BusinessException e = assertThrows(BusinessException.class,
                () -> familyService.updateFamily(1L, Map.of("name", ""), 100L));
        assertEquals(ErrorCode.VALIDATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldRejectInvalidAvatarUrl() {
        when(familyMapper.findById(anyLong())).thenReturn(Optional.of(new Family()));

        BusinessException e = assertThrows(BusinessException.class,
                () -> familyService.updateFamily(1L, Map.of("avatar", "ftp://invalid"), 100L));
        assertEquals(ErrorCode.VALIDATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldAcceptNullAvatar() {
        Long familyId = 1L;
        Family family = new Family();
        family.setId(familyId);

        when(familyMapper.findById(familyId)).thenReturn(Optional.of(family));
        doReturn(1).when(familyMapper).updateById(any(Family.class));
        when(familyMemberMapper.findActiveByFamilyId(anyLong())).thenReturn(List.of());
        when(childProfileMapper.findActiveByFamilyId(anyLong())).thenReturn(List.of());

        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("avatar", null);
        Map<String, Object> result = familyService.updateFamily(familyId, updates, 100L);
        assertNull(result.get("avatar"));
    }
}
