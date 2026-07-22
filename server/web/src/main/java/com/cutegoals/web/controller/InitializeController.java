package com.cutegoals.web.controller;

import com.cutegoals.auth.service.AuthenticationService;
import com.cutegoals.auth.service.AuthenticationService.LoginResult;
import com.cutegoals.auth.service.InitializationService;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.dto.auth.InitializeRequest;
import com.cutegoals.auth.config.TokenCookieWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
    private final AuthenticationService authenticationService;
    private final TokenCookieWriter tokenCookieWriter;

    /**
     * POST /api/auth/initialize
     * Complete instance initialization: consume token → create account → create family → auto-login.
     *
     * @param request  initialization request body (token, phone, password)
     * @param response HTTP response (for setting auth cookies)
     * @return 200 with account + login info on success, 403 on failure
     */
    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initialize(
            @RequestBody InitializeRequest request,
            HttpServletResponse response) {

        // Generate request ID for traceability
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // Step 1: Perform initialization (consume token → create account → create family)
        Long accountId = initializationService.initialize(
                request.getToken(), request.getPhone(), request.getPassword());

        // Step 2: Auto-login to create session and get tokens
        LoginResult loginResult = authenticationService.login(
                request.getPhone(), request.getPassword());

        // Step 3: Set auth cookies (access + refresh + CSRF)
        tokenCookieWriter.setTokenCookies(response, loginResult.accessToken(), loginResult.refreshToken());
        tokenCookieWriter.setCsrfCookie(response);

        log.info("Instance initialized successfully: accountId={}, requestId={}", accountId, requestId);

        // Step 4: Return full session data (aligns with login response + initialized flag)
        Map<String, Object> data = new HashMap<>();
        data.put("accountId", loginResult.accountId());
        data.put("phone", loginResult.phone());
        data.put("roles", loginResult.roles());
        data.put("familyId", loginResult.familyId());
        data.put("initialized", true);
        data.put("expiresIn", loginResult.expiresIn());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, requestId));
    }
}
