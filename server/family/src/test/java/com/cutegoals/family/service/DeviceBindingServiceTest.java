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
class DeviceBindingServiceTest {

    @Mock private DeviceBindingMapper deviceBindingMapper;
    @Mock private ChildProfileMapper childProfileMapper;
    @Mock private FamilyMemberMapper familyMemberMapper;
    @Mock private SessionService sessionService;
    @Mock private AuditService auditService;

    private BCryptPasswordEncoder passwordEncoder;
    private DeviceBindingService deviceBindingService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        deviceBindingService = new DeviceBindingService(
                deviceBindingMapper, childProfileMapper, familyMemberMapper,
                sessionService, auditService, passwordEncoder);
    }

    // ==================== Bind Device ====================

    @Test
    void shouldBindDeviceSuccessfully() {
        String deviceId = "device-123";
        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.empty());
        doReturn(1).when(deviceBindingMapper).insert(any(DeviceBinding.class));

        Map<String, Object> result = deviceBindingService.bindDevice(1L, deviceId, 100L);

        assertEquals(deviceId, result.get("deviceId"));
        assertEquals(1L, result.get("familyId"));
        assertNotNull(result.get("credential"));
        verify(deviceBindingMapper).insert(any(DeviceBinding.class));
        verify(auditService).record(eq("DEVICE_BOUND"), eq(100L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldReturnExistingBindingForAlreadyBoundDevice() {
        String deviceId = "device-123";
        DeviceBinding existing = new DeviceBinding();
        existing.setId(10L);
        existing.setDeviceId(deviceId);
        existing.setFamilyId(1L);
        existing.setStatus("ACTIVE");

        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(existing));

        Map<String, Object> result = deviceBindingService.bindDevice(1L, deviceId, 100L);

        assertEquals(10L, result.get("id"));
        assertNull(result.get("credential")); // credential not returned for existing
        verify(deviceBindingMapper, never()).insert(any(DeviceBinding.class));
    }

    @Test
    void shouldRejectBindDeviceFromDifferentFamily() {
        String deviceId = "device-123";
        DeviceBinding existing = new DeviceBinding();
        existing.setId(10L);
        existing.setDeviceId(deviceId);
        existing.setFamilyId(2L); // bound to family 2
        existing.setStatus("ACTIVE");

        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(existing));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deviceBindingService.bindDevice(1L, deviceId, 100L));
        assertEquals(ErrorCode.SINGLE_FAMILY_ONLY, e.getErrorCode());
    }

    // ==================== Revoke Device Binding ====================

    @Test
    void shouldRevokeDeviceBindingAndChildSessions() {
        Long bindingId = 10L;
        String deviceId = "device-123";

        DeviceBinding binding = new DeviceBinding();
        binding.setId(bindingId);
        binding.setDeviceId(deviceId);
        binding.setFamilyId(1L);
        binding.setStatus("ACTIVE");

        when(deviceBindingMapper.findById(bindingId)).thenReturn(Optional.of(binding));
        when(deviceBindingMapper.revokeById(bindingId)).thenReturn(1);

        deviceBindingService.revokeDeviceBinding(bindingId, 1L, 100L);

        verify(deviceBindingMapper).revokeById(bindingId);
        verify(sessionService).revokeSessionsByDevice(deviceId);
        verify(auditService).record(eq("DEVICE_REVOKED"), eq(100L), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldRejectRevokeForWrongFamily() {
        DeviceBinding binding = new DeviceBinding();
        binding.setId(10L);
        binding.setFamilyId(2L);
        binding.setStatus("ACTIVE");

        when(deviceBindingMapper.findById(10L)).thenReturn(Optional.of(binding));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deviceBindingService.revokeDeviceBinding(10L, 1L, 100L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
        verify(deviceBindingMapper, never()).revokeById(anyLong());
        verify(sessionService, never()).revokeSessionsByDevice(anyString());
    }

    @Test
    void shouldRejectRevokeNonActiveBinding() {
        DeviceBinding binding = new DeviceBinding();
        binding.setId(10L);
        binding.setFamilyId(1L);
        binding.setStatus("REVOKED");

        when(deviceBindingMapper.findById(10L)).thenReturn(Optional.of(binding));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deviceBindingService.revokeDeviceBinding(10L, 1L, 100L));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    // ==================== Get Children For Device ====================

    @Test
    void shouldGetChildrenForDevice() {
        String deviceId = "device-123";
        DeviceBinding binding = new DeviceBinding();
        binding.setId(10L);
        binding.setFamilyId(1L);
        binding.setStatus("ACTIVE");

        ChildProfile child = new ChildProfile();
        child.setId(20L);
        child.setNickname("小明");
        child.setAvatar("/kid.png");
        child.setStatus("ACTIVE");
        child.setPinHash(passwordEncoder.encode("1234"));

        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(binding));
        when(childProfileMapper.findActiveOnlyByFamilyId(1L)).thenReturn(List.of(child));

        List<Map<String, Object>> children = deviceBindingService.getChildrenForDevice(deviceId);

        assertEquals(1, children.size());
        assertEquals("小明", children.get(0).get("nickname"));
        assertTrue((Boolean) children.get(0).get("hasPin"));
        assertNull(children.get(0).get("pinHash"));
    }

    @Test
    void shouldRejectGetChildrenForUnboundDevice() {
        String deviceId = "unbound-device";
        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> deviceBindingService.getChildrenForDevice(deviceId));
        assertEquals(ErrorCode.DEVICE_NOT_AUTHORIZED, e.getErrorCode());
    }

    // ==================== Child PIN Login ====================

    @Test
    void shouldLoginChildWithCorrectPin() {
        String deviceId = "device-123";
        Long childId = 20L;
        String pin = "1234";

        DeviceBinding binding = new DeviceBinding();
        binding.setId(10L);
        binding.setDeviceId(deviceId);
        binding.setFamilyId(1L);
        binding.setStatus("ACTIVE");

        ChildProfile profile = new ChildProfile();
        profile.setId(childId);
        profile.setFamilyId(1L);
        profile.setNickname("小明");
        profile.setStatus("ACTIVE");
        profile.setPinHash(passwordEncoder.encode(pin));

        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(binding));
        when(childProfileMapper.findById(childId)).thenReturn(Optional.of(profile));
        when(sessionService.createChildSession(childId, deviceId)).thenReturn("session-abc");

        Map<String, Object> result = deviceBindingService.childLogin(deviceId, childId, pin);

        assertEquals("session-abc", result.get("sessionId"));
        assertEquals(childId, result.get("childId"));
        assertEquals("小明", result.get("nickname"));
        verify(sessionService).createChildSession(childId, deviceId);
        verify(auditService).record(eq("CHILD_LOGIN_SUCCESS"), isNull(), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldRejectLoginWithWrongPin() {
        String deviceId = "device-123";
        Long childId = 20L;
        String correctPin = "1234";
        String wrongPin = "9999";

        DeviceBinding binding = new DeviceBinding();
        binding.setId(10L);
        binding.setDeviceId(deviceId);
        binding.setFamilyId(1L);
        binding.setStatus("ACTIVE");

        ChildProfile profile = new ChildProfile();
        profile.setId(childId);
        profile.setFamilyId(1L);
        profile.setStatus("ACTIVE");
        profile.setPinHash(passwordEncoder.encode(correctPin));

        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(binding));
        when(childProfileMapper.findById(childId)).thenReturn(Optional.of(profile));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deviceBindingService.childLogin(deviceId, childId, wrongPin));
        assertEquals(ErrorCode.CHILD_AUTHENTICATION_FAILED, e.getErrorCode());
        verify(auditService).record(eq("CHILD_LOGIN_FAILED"), isNull(), eq("FAILED"), anyString());
    }

    @Test
    void shouldLockAfterFiveFailedPinAttempts() {
        String deviceId = "device-123";
        Long childId = 20L;
        String correctPin = "1234";

        DeviceBinding binding = new DeviceBinding();
        binding.setId(10L);
        binding.setDeviceId(deviceId);
        binding.setFamilyId(1L);
        binding.setStatus("ACTIVE");

        ChildProfile profile = new ChildProfile();
        profile.setId(childId);
        profile.setFamilyId(1L);
        profile.setStatus("ACTIVE");
        profile.setPinHash(passwordEncoder.encode(correctPin));

        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(binding));
        when(childProfileMapper.findById(childId)).thenReturn(Optional.of(profile));

        // Fail 5 times
        for (int i = 0; i < 5; i++) {
            try {
                deviceBindingService.childLogin(deviceId, childId, "wrong");
            } catch (BusinessException e) {
                // Expected
            }
        }

        // 6th attempt should be locked
        BusinessException e = assertThrows(BusinessException.class,
                () -> deviceBindingService.childLogin(deviceId, childId, correctPin));
        assertEquals(ErrorCode.PIN_LOCKED, e.getErrorCode());
        // Should have recorded PIN_LOCKED audit at 5th failure
        verify(auditService, atLeastOnce()).record(eq("PIN_LOCKED"), isNull(), eq("FAILED"), anyString());
    }

    @Test
    void shouldRejectLoginForUnboundDevice() {
        String deviceId = "unbound-device";
        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> deviceBindingService.childLogin(deviceId, 20L, "1234"));
        assertEquals(ErrorCode.DEVICE_NOT_AUTHORIZED, e.getErrorCode());
    }

    @Test
    void shouldRejectLoginForWrongFamilyChild() {
        String deviceId = "device-123";

        DeviceBinding binding = new DeviceBinding();
        binding.setId(10L);
        binding.setDeviceId(deviceId);
        binding.setFamilyId(1L);
        binding.setStatus("ACTIVE");

        ChildProfile profile = new ChildProfile();
        profile.setId(20L);
        profile.setFamilyId(2L); // different family
        profile.setStatus("ACTIVE");
        profile.setPinHash(passwordEncoder.encode("1234"));

        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(binding));
        when(childProfileMapper.findById(20L)).thenReturn(Optional.of(profile));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deviceBindingService.childLogin(deviceId, 20L, "1234"));
        assertEquals(ErrorCode.CHILD_AUTHENTICATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldRejectLoginForInactiveChild() {
        String deviceId = "device-123";

        DeviceBinding binding = new DeviceBinding();
        binding.setId(10L);
        binding.setDeviceId(deviceId);
        binding.setFamilyId(1L);
        binding.setStatus("ACTIVE");

        ChildProfile profile = new ChildProfile();
        profile.setId(20L);
        profile.setFamilyId(1L);
        profile.setStatus("INACTIVE"); // inactive child
        profile.setPinHash(passwordEncoder.encode("1234"));

        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(binding));
        when(childProfileMapper.findById(20L)).thenReturn(Optional.of(profile));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deviceBindingService.childLogin(deviceId, 20L, "1234"));
        assertEquals(ErrorCode.CHILD_AUTHENTICATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldRejectLoginForChildWithoutPin() {
        String deviceId = "device-123";

        DeviceBinding binding = new DeviceBinding();
        binding.setId(10L);
        binding.setDeviceId(deviceId);
        binding.setFamilyId(1L);
        binding.setStatus("ACTIVE");

        ChildProfile profile = new ChildProfile();
        profile.setId(20L);
        profile.setFamilyId(1L);
        profile.setStatus("ACTIVE");
        profile.setPinHash(null); // no PIN set

        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(binding));
        when(childProfileMapper.findById(20L)).thenReturn(Optional.of(profile));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deviceBindingService.childLogin(deviceId, 20L, "1234"));
        assertEquals(ErrorCode.CHILD_AUTHENTICATION_FAILED, e.getErrorCode());
    }

    @Test
    void shouldClearLockoutAfterSuccessfulLogin() {
        String deviceId = "device-123";
        Long childId = 20L;
        String correctPin = "1234";

        DeviceBinding binding = new DeviceBinding();
        binding.setId(10L);
        binding.setDeviceId(deviceId);
        binding.setFamilyId(1L);
        binding.setStatus("ACTIVE");

        ChildProfile profile = new ChildProfile();
        profile.setId(childId);
        profile.setFamilyId(1L);
        profile.setStatus("ACTIVE");
        profile.setPinHash(passwordEncoder.encode(correctPin));

        when(deviceBindingMapper.findActiveByDeviceId(deviceId)).thenReturn(Optional.of(binding));
        when(childProfileMapper.findById(childId)).thenReturn(Optional.of(profile));

        // Fail 4 times
        for (int i = 0; i < 4; i++) {
            try {
                deviceBindingService.childLogin(deviceId, childId, "wrong");
            } catch (BusinessException e) {
                // Expected
            }
        }

        // Now succeed (5th attempt is still allowed - lock happens at 5 failures, not on 5th try)
        // Actually re-setup mocks since childLogin may update internal state
        when(sessionService.createChildSession(childId, deviceId)).thenReturn("session-abc");

        Map<String, Object> result = deviceBindingService.childLogin(deviceId, childId, correctPin);
        assertEquals("session-abc", result.get("sessionId"));
        verify(sessionService).createChildSession(childId, deviceId);
    }
}
