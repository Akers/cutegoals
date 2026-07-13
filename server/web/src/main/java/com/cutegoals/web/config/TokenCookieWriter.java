package com.cutegoals.web.config;

import com.cutegoals.common.constant.AuthConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Shared component for writing authentication cookies (access token, refresh token, CSRF token).
 * <p>
 * Extracted from AuthController so that multiple controllers (e.g. InitializeController)
 * can set cookies without duplicating the cookie-building logic.
 */
@Component
public class TokenCookieWriter {

    private static final SecureRandom CSRF_RANDOM = new SecureRandom();

    @Value("${app.production:false}")
    private boolean production;

    /**
     * Set HttpOnly access-token and refresh-token cookies.
     */
    public void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        // Access token cookie: HttpOnly, Secure in production, SameSite=Lax
        ResponseCookie accessCookie = ResponseCookie.from(AuthConstants.COOKIE_ACCESS_TOKEN, accessToken)
                .httpOnly(true)
                .secure(production)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(AuthConstants.JWT_ACCESS_EXPIRY_MINUTES))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        // Refresh token cookie
        ResponseCookie refreshCookie = ResponseCookie.from(AuthConstants.COOKIE_REFRESH_TOKEN, refreshToken)
                .httpOnly(true)
                .secure(production)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(AuthConstants.REFRESH_TOKEN_EXPIRY_DAYS))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    /**
     * Set a CSRF token cookie (non-HttpOnly) using the given token value.
     */
    public void setCsrfCookie(HttpServletResponse response, String csrfToken) {
        ResponseCookie csrfCookie = ResponseCookie.from(AuthConstants.COOKIE_CSRF_TOKEN, csrfToken)
                .httpOnly(false)
                .secure(production)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(AuthConstants.JWT_ACCESS_EXPIRY_MINUTES))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
    }

    /**
     * Generate a new CSRF token and set the cookie.
     */
    public void setCsrfCookie(HttpServletResponse response) {
        setCsrfCookie(response, generateCsrfToken());
    }

    /**
     * Clear access-token and refresh-token cookies (set maxAge=0).
     */
    public void clearTokenCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from(AuthConstants.COOKIE_ACCESS_TOKEN, "")
                .httpOnly(true)
                .secure(production)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        ResponseCookie refreshCookie = ResponseCookie.from(AuthConstants.COOKIE_REFRESH_TOKEN, "")
                .httpOnly(true)
                .secure(production)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    /**
     * Generate a 64-hex-char random CSRF token.
     */
    public String generateCsrfToken() {
        byte[] bytes = new byte[32];
        CSRF_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
