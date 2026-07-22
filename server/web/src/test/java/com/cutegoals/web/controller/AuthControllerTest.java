package com.cutegoals.web.controller;

import com.cutegoals.auth.service.AuditService;
import com.cutegoals.auth.service.AuthenticationService;
import com.cutegoals.auth.service.SessionService;
import com.cutegoals.auth.service.TokenService;
import com.cutegoals.auth.service.TokenService.JwtClaims;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.auth.config.TokenCookieWriter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthController, focusing on GET /api/auth/me.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private TokenService tokenService;

    @Mock
    private SessionService sessionService;

    @Mock
    private AuditService auditService;

    @Mock
    private TokenCookieWriter tokenCookieWriter;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                authenticationService, tokenService, sessionService,
                auditService, tokenCookieWriter);
    }

    @Test
    void meShouldReturnAccountWhenAuthenticated() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("access_token", "valid-jwt-token")
        });
        when(tokenService.parseAccessToken("valid-jwt-token"))
                .thenReturn(new JwtClaims(42L, List.of("PARENT"), "session-1", null, null));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.me(request);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(42L, response.getBody().getData().get("accountId"));
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) response.getBody().getData().get("roles");
        assertEquals(List.of("PARENT"), roles);
    }

    @Test
    void meShouldThrow401WhenNoToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null);
        when(tokenService.parseAccessToken(null))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertThrows(BusinessException.class, () -> controller.me(request));
    }

    @Test
    void meShouldThrow401WhenInvalidToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("access_token", "invalid-token")
        });
        when(tokenService.parseAccessToken("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertThrows(BusinessException.class, () -> controller.me(request));
    }
}
