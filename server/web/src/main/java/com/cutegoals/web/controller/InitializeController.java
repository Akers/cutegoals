package com.cutegoals.web.controller;

import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.dto.auth.InitializeRequest;
import com.cutegoals.auth.service.InitializationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for instance initialization.
 * Task 2.1 + 2.2: Consume initialization token, create account + family in single transaction.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class InitializeController {

    private static final Logger log = LoggerFactory.getLogger(InitializeController.class);

    private final InitializationService initializationService;

    /**
     * POST /api/auth/initialize
     * Complete instance initialization: consume token → create account → create family.
     *
     * @param request initialization request body (token, phone, password)
     * @return 200 with account info on success, 403 on failure
     */
    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initialize(
            @RequestBody InitializeRequest request) {

        // Generate request ID for traceability
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // The initialization service handles all validation and transaction
        Long accountId = initializationService.initialize(
                request.getToken(), request.getPhone(), request.getPassword());

        log.info("Instance initialized successfully: accountId={}, requestId={}", accountId, requestId);

        Map<String, Object> data = Map.of(
                "accountId", accountId,
                "initialized", true
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, requestId));
    }
}
