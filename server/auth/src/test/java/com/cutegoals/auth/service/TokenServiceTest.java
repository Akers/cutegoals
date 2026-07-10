package com.cutegoals.auth.service;

import com.cutegoals.auth.mapper.RefreshTokenMapper;
import com.cutegoals.common.entity.auth.RefreshToken;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TokenService.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(refreshTokenMapper);
        ReflectionTestUtils.setField(tokenService, "jwtSecret", "test-secret-key-that-is-at-least-32-bytes-long!!");
        ReflectionTestUtils.setField(tokenService, "accessExpiryMinutes", 15);
        ReflectionTestUtils.setField(tokenService, "refreshExpiryDays", 7);
        tokenService.init();
    }

    @Test
    void shouldGenerateAccessToken() {
        String token = tokenService.generateAccessToken(
                1L, List.of("INSTANCE_ADMIN", "PARENT"), "session-123");
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts
    }

    @Test
    void shouldParseAccessToken() {
        String token = tokenService.generateAccessToken(
                1L, List.of("INSTANCE_ADMIN"), "session-456");

        var claims = tokenService.parseAccessToken(token);
        assertEquals(1L, claims.accountId());
        assertEquals(List.of("INSTANCE_ADMIN"), claims.roles());
        assertEquals("session-456", claims.sessionId());
    }

    @Test
    void shouldRejectInvalidToken() {
        assertThrows(BusinessException.class,
                () -> tokenService.parseAccessToken("invalid.token.here"));
    }

    @Test
    void shouldGenerateRefreshToken() {
        when(refreshTokenMapper.insert(any(RefreshToken.class))).thenReturn(1);

        String token = tokenService.generateRefreshToken("session-789");
        assertNotNull(token);
        assertEquals(64, token.length()); // 32 bytes = 64 hex chars

        verify(refreshTokenMapper).insert(any(RefreshToken.class));
    }

    @Test
    void shouldRefreshTokens() {
        String oldToken = "oldRefreshToken";
        String tokenHash = TokenService.hashToken(oldToken);

        RefreshToken dbToken = new RefreshToken();
        dbToken.setId(1L);
        dbToken.setTokenHash(tokenHash);
        dbToken.setSessionId("session-111");
        dbToken.setFamilyId("family-111");
        dbToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        dbToken.setRevoked(false);

        when(refreshTokenMapper.findByTokenHash(tokenHash)).thenReturn(Optional.of(dbToken));
        when(refreshTokenMapper.revokeById(1L)).thenReturn(1);
        when(refreshTokenMapper.insert(any(RefreshToken.class))).thenReturn(1);

        var result = tokenService.refreshTokens(oldToken);
        assertNotNull(result.newRefreshToken());
        assertEquals("session-111", result.sessionId());

        verify(refreshTokenMapper).revokeById(1L);
    }

    @Test
    void shouldRejectExpiredRefreshToken() {
        String tokenHash = TokenService.hashToken("expiredRefresh");
        RefreshToken dbToken = new RefreshToken();
        dbToken.setTokenHash(tokenHash);
        dbToken.setExpiresAt(LocalDateTime.now().minusDays(1));
        dbToken.setRevoked(false);

        when(refreshTokenMapper.findByTokenHash(tokenHash)).thenReturn(Optional.of(dbToken));

        BusinessException e = assertThrows(BusinessException.class,
                () -> tokenService.refreshTokens("expiredRefresh"));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, e.getErrorCode());
    }

    @Test
    void shouldDetectReusedRefreshToken() {
        String tokenHash = TokenService.hashToken("reusedRefresh");
        RefreshToken dbToken = new RefreshToken();
        dbToken.setId(1L);
        dbToken.setTokenHash(tokenHash);
        dbToken.setFamilyId("family-reuse");
        dbToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        dbToken.setRevoked(true); // Already revoked = reused

        when(refreshTokenMapper.findByTokenHash(tokenHash)).thenReturn(Optional.of(dbToken));
        when(refreshTokenMapper.revokeFamily("family-reuse")).thenReturn(1);

        BusinessException e = assertThrows(BusinessException.class,
                () -> tokenService.refreshTokens("reusedRefresh"));
        assertEquals(ErrorCode.REFRESH_TOKEN_REUSED, e.getErrorCode());
        verify(refreshTokenMapper).revokeFamily("family-reuse");
    }

    @Test
    void shouldHashTokenConsistently() {
        String hash1 = TokenService.hashToken("refresh123");
        String hash2 = TokenService.hashToken("refresh123");
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }
}
