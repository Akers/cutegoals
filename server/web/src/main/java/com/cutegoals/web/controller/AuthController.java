package com.cutegoals.web.controller;

import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.auth.service.AuthenticationService;
import com.cutegoals.auth.service.AuthenticationService.LoginResult;
import com.cutegoals.auth.service.SessionService;
import com.cutegoals.auth.service.TokenService;
import com.cutegoals.auth.service.TokenService.JwtClaims;
import com.cutegoals.auth.service.TokenService.TokenRefreshResult;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.dto.auth.*;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.HexFormat;

/**
 * REST controller for authentication operations.
 * Tasks 2.3, 2.5, 2.6: Login, token refresh, logout, password change.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationService authenticationService;
    private final TokenService tokenService;
    private final SessionService sessionService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /**
     * POST /api/auth/login
     * Phone + password login.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response) {

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("requestId", requestId);

        LoginResult result = authenticationService.login(request.getPhone(), request.getPassword());

        // Set cookies (tokens via HttpOnly, CSRF via non-HttpOnly)
        setTokenCookies(response, result.accessToken(), result.refreshToken());
        String csrfToken = generateCsrfToken();
        setCsrfCookie(response, csrfToken);

        Map<String, Object> data = new HashMap<>();
        data.put("accountId", result.accountId());
        data.put("phone", result.phone());
        data.put("roles", result.roles());
        data.put("familyId", result.familyId());
        data.put("expiresIn", result.expiresIn());

        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    /**
     * POST /api/auth/refresh
     * Refresh access token using refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            HttpServletRequest request, HttpServletResponse response) {

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("requestId", requestId);

        // Extract refresh token from cookie or body
        String refreshToken = extractRefreshToken(request);

        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        TokenRefreshResult result = tokenService.refreshTokens(refreshToken);

        // Get current account from old access token before rotation
        String oldAccessToken = extractAccessToken(request);
        JwtClaims claims = tokenService.parseAccessToken(oldAccessToken);

        // Generate new access token
        String newAccessToken = tokenService.generateAccessToken(
                claims.accountId(), claims.roles(), result.sessionId());

        // Set new cookies (HttpOnly — JS cannot read them)
        setTokenCookies(response, newAccessToken, result.newRefreshToken());

        Map<String, Object> data = new HashMap<>();
        // Per spec: browser-available scripts MUST NOT be able to read access or refresh tokens
        data.put("expiresIn", AuthConstants.JWT_ACCESS_EXPIRY_MINUTES * 60);

        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    /**
     * POST /api/auth/logout
     * Logout: revoke current session and clear cookies.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request,
                                                     HttpServletResponse response) {

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("requestId", requestId);

        // Get session from token
        String accessToken = extractAccessToken(request);
        Long loggedOutAccountId = null;
        if (accessToken != null) {
            try {
                JwtClaims claims = tokenService.parseAccessToken(accessToken);
                loggedOutAccountId = claims.accountId();
                sessionService.revokeSession(claims.sessionId());
            } catch (BusinessException e) {
                // Session already invalid, still clear cookies
            }
        }

        // Clear cookies
        clearTokenCookies(response);

        if (loggedOutAccountId != null) {
            auditService.record(AuditEvent.LOGOUT, loggedOutAccountId, "SUCCESS",
                    "User logged out: accountId=" + loggedOutAccountId);
        }

        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }

    /**
     * PUT /api/auth/password
     * Change password: verifies old password, updates hash, revokes all sessions.
     */
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put("requestId", requestId);

        Long accountId = (Long) httpRequest.getAttribute(AuthConstants.ATTR_ACCOUNT_ID);

        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Verify old password, validate new password, update hash, revoke sessions
        authenticationService.changePassword(accountId, request.getOldPassword(), request.getNewPassword());

        // Clear cookies
        clearTokenCookies(httpResponse);

        log.info("Password changed and sessions revoked: accountId={}", accountId);

        return ResponseEntity.ok(ApiResponse.success(null, requestId));
    }

    /**
     * POST /api/auth/sms/send
     * Send SMS verification code (only if SMS provider is configured).
     */
    @PostMapping("/sms/send")
    public ResponseEntity<ApiResponse<Void>> sendSms(@RequestBody Map<String, String> body) {
        // Placeholder - returns SMS_LOGIN_NOT_CONFIGURED by default
        // Will be provided by the real SmsAuthProvider when configured
        throw new BusinessException(ErrorCode.SMS_LOGIN_NOT_CONFIGURED);
    }

    /**
     * POST /api/auth/sms/verify
     * Verify SMS code and login (only if SMS provider is configured).
     */
    @PostMapping("/sms/login")
    public ResponseEntity<ApiResponse<Void>> smsLogin(@RequestBody Map<String, String> body) {
        throw new BusinessException(ErrorCode.SMS_LOGIN_NOT_CONFIGURED);
    }

    // === CSRF Token ===

    private static final SecureRandom CSRF_RANDOM = new SecureRandom();

    private String generateCsrfToken() {
        byte[] bytes = new byte[32];
        CSRF_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Set CSRF token cookie (non-HttpOnly so JS can read it) and the XSRF-TOKEN.
     * The client must send the token back via the X-XSRF-Token header.
     */
    private void setCsrfCookie(HttpServletResponse response, String csrfToken) {
        ResponseCookie csrfCookie = ResponseCookie.from(AuthConstants.COOKIE_CSRF_TOKEN, csrfToken)
                .httpOnly(false)   // Must be readable by JS (double-submit pattern)
                .secure(false)     // Set to true in production
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(AuthConstants.JWT_ACCESS_EXPIRY_MINUTES))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
    }

    // === Cookie Helpers ===

    private void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        // Access token cookie: HttpOnly, Secure in production, SameSite=Lax
        ResponseCookie accessCookie = ResponseCookie.from(AuthConstants.COOKIE_ACCESS_TOKEN, accessToken)
                .httpOnly(true)
                .secure(false) // Set to true in production when using HTTPS
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(AuthConstants.JWT_ACCESS_EXPIRY_MINUTES))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        // Refresh token cookie
        ResponseCookie refreshCookie = ResponseCookie.from(AuthConstants.COOKIE_REFRESH_TOKEN, refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(AuthConstants.REFRESH_TOKEN_EXPIRY_DAYS))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearTokenCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from(AuthConstants.COOKIE_ACCESS_TOKEN, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        ResponseCookie refreshCookie = ResponseCookie.from(AuthConstants.COOKIE_REFRESH_TOKEN, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AuthConstants.COOKIE_REFRESH_TOKEN.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        // Fallback to Authorization header
        String authHeader = request.getHeader("X-Refresh-Token");
        if (authHeader != null) {
            return authHeader;
        }
        return null;
    }

    private String extractAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AuthConstants.COOKIE_ACCESS_TOKEN.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
