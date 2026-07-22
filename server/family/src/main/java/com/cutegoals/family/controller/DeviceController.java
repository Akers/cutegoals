package com.cutegoals.family.controller;

import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.mapper.DeviceBindingMapper;
import com.cutegoals.family.service.DeviceBindingService;
import com.cutegoals.auth.service.TokenService;
import com.cutegoals.auth.config.TokenCookieWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for device binding and child authentication (Task 2.12).
 */
@RestController
@RequiredArgsConstructor
public class DeviceController {

    private static final Logger log = LoggerFactory.getLogger(DeviceController.class);

    private final DeviceBindingService deviceBindingService;
    private final DeviceBindingMapper deviceBindingMapper;
    private final TokenService tokenService;
    private final TokenCookieWriter tokenCookieWriter;

    /**
     * POST /api/family/devices/bind - Parent authorizes a device.
     */
    @PostMapping("/api/family/devices/bind")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bindDevice(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);
        Long familyId = getFamilyId(request);

        String deviceId = (String) body.get("deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "deviceId is required");
        }

        Map<String, Object> result = deviceBindingService.bindDevice(familyId, deviceId, accountId);
        return ResponseEntity.ok(ApiResponse.success(result, requestId));
    }

    /**
     * DELETE /api/family/devices/{id} - Parent revokes a device binding.
     */
    @DeleteMapping("/api/family/devices/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeDeviceBinding(
            @PathVariable Long id,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        Long accountId = getAccountId(request);
        Long familyId = getFamilyId(request);

        deviceBindingService.revokeDeviceBinding(id, familyId, accountId);
        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }

    /**
     * GET /api/family/devices/children - Get children for a device (pre-PIN login).
     * Uses deviceId as a query parameter.
     */
    @GetMapping("/api/family/devices/children")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getChildrenForDevice(
            @RequestParam String deviceId,
            HttpServletRequest request) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        List<Map<String, Object>> children = deviceBindingService.getChildrenForDevice(deviceId);
        return ResponseEntity.ok(ApiResponse.success(children, requestId));
    }

    /**
     * POST /api/auth/child/login - Child PIN login on a bound device.
     * This endpoint is unauthenticated (device credential + PIN).
     */
    @PostMapping("/api/auth/child/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> childLogin(
            @RequestBody Map<String, Object> body,
            HttpServletResponse response) {
        String requestId = generateRequestId();
        MDC.put("requestId", requestId);

        String deviceId = (String) body.get("deviceId");
        Object childIdRaw = body.get("childId");
        String pin = (String) body.get("pin");

        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "deviceId is required");
        }
        if (childIdRaw == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "childId is required");
        }
        if (pin == null || pin.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "pin is required");
        }

        Long childId = childIdRaw instanceof Number n ? n.longValue() : Long.valueOf(childIdRaw.toString());

        Map<String, Object> result = deviceBindingService.childLogin(deviceId, childId, pin);

        // Generate JWT and set cookies for session persistence
        String sessionId = (String) result.get("sessionId");
        Long familyId = result.get("familyId") instanceof Number n ? n.longValue() : null;
        String accessToken = tokenService.generateAccessToken(childId, List.of("CHILD"), sessionId, childId, familyId);
        String refreshToken = tokenService.generateRefreshToken(sessionId);
        tokenCookieWriter.setTokenCookies(response, accessToken, refreshToken);
        tokenCookieWriter.setCsrfCookie(response);

        return ResponseEntity.ok(ApiResponse.success(result, requestId));
    }

    // === Helpers ===

    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private Long getAccountId(HttpServletRequest request) {
        Long id = (Long) request.getAttribute(AuthConstants.ATTR_ACCOUNT_ID);
        if (id == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    private Long getFamilyId(HttpServletRequest request) {
        Long id = (Long) request.getAttribute(AuthConstants.ATTR_FAMILY_ID);
        return id;
    }
}
