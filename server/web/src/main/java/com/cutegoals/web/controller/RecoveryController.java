package com.cutegoals.web.controller;

import com.cutegoals.auth.service.RecoveryService;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.dto.auth.RecoveryRequest;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for admin recovery operations.
 * Task 2.7: Recovery endpoint that validates the one-time recovery token.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RecoveryController {

    private static final Logger log = LoggerFactory.getLogger(RecoveryController.class);

    private final RecoveryService recoveryService;

    /**
     * POST /api/auth/recover/initiate
     * Initiate admin recovery. Only accessible from localhost.
     * Generates a one-time recovery token valid for 15 minutes.
     */
    @PostMapping("/recover/initiate")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> initiate(
            HttpServletRequest httpRequest) {

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("requestId", requestId);

        // Only allow localhost
        String remoteAddr = httpRequest.getRemoteAddr();
        if (!"127.0.0.1".equals(remoteAddr) && !"::1".equals(remoteAddr)
                && !"0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            log.warn("Recovery initiation attempted from non-local address: {}", remoteAddr);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String recoveryToken = recoveryService.initiateRecovery();

        log.info("Recovery flow initiated, requestId={}", requestId);

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("recoveryToken", recoveryToken);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    /**
     * POST /api/auth/recover
     * Complete admin recovery with a one-time recovery token and new password.
     * The recovery token must be generated locally via deploy/recover-admin.sh.
     */
    @PostMapping("/recover")
    public ResponseEntity<ApiResponse<Void>> recover(
            @RequestBody RecoveryRequest request,
            HttpServletRequest httpRequest) {

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("requestId", requestId);

        // For extra safety, also check that this is called from localhost
        String remoteAddr = httpRequest.getRemoteAddr();
        if (!"127.0.0.1".equals(remoteAddr) && !"::1".equals(remoteAddr)
                && !"0:0:0:0:0:0:0:1".equals(remoteAddr)) {
            log.warn("Recovery attempted from non-local address: {}", remoteAddr);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        recoveryService.completeRecovery(request.getRecoveryToken(), request.getNewPassword());

        log.info("Password recovery completed successfully, requestId={}", requestId);

        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }
}
