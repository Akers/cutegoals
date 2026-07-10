package com.cutegoals.auth.service;

import com.cutegoals.auth.mapper.InitializationTokenMapper;
import com.cutegoals.common.entity.auth.InitializationToken;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InitializationTokenService.
 */
@ExtendWith(MockitoExtension.class)
class InitializationTokenServiceTest {

    @Mock
    private InitializationTokenMapper tokenMapper;

    private InitializationTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new InitializationTokenService(tokenMapper);
    }

    @Test
    void shouldGenerateToken() {
        when(tokenMapper.insert(any(InitializationToken.class))).thenReturn(1);

        String token = tokenService.generateToken();

        assertNotNull(token);
        assertEquals(64, token.length()); // 32 bytes = 64 hex chars
        verify(tokenMapper).insert(any(InitializationToken.class));
    }

    @Test
    void shouldConsumeValidToken() {
        String token = "testToken123";
        String tokenHash = InitializationTokenService.hashToken(token);
        InitializationToken dbToken = new InitializationToken();
        dbToken.setId(1L);
        dbToken.setTokenHash(tokenHash);
        dbToken.setConsumed(false);
        dbToken.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(tokenMapper.countConsumedTokens()).thenReturn(0L);
        when(tokenMapper.findByTokenHash(tokenHash)).thenReturn(Optional.of(dbToken));
        when(tokenMapper.consumeToken(1L)).thenReturn(1);

        assertDoesNotThrow(() -> tokenService.consumeToken(token));
        verify(tokenMapper).consumeToken(1L);
    }

    @Test
    void shouldRejectNullToken() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> tokenService.consumeToken(null));
        assertEquals(ErrorCode.INITIALIZATION_NOT_ALLOWED, e.getErrorCode());
    }

    @Test
    void shouldRejectAlreadyInitialized() {
        when(tokenMapper.countConsumedTokens()).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> tokenService.consumeToken("someToken"));
        assertEquals(ErrorCode.INITIALIZATION_NOT_ALLOWED, e.getErrorCode());
    }

    @Test
    void shouldRejectConsumedToken() {
        String tokenHash = InitializationTokenService.hashToken("usedToken");
        InitializationToken dbToken = new InitializationToken();
        dbToken.setId(1L);
        dbToken.setTokenHash(tokenHash);
        dbToken.setConsumed(true);
        dbToken.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(tokenMapper.countConsumedTokens()).thenReturn(0L);
        when(tokenMapper.findByTokenHash(tokenHash)).thenReturn(Optional.of(dbToken));

        BusinessException e = assertThrows(BusinessException.class,
                () -> tokenService.consumeToken("usedToken"));
        assertEquals(ErrorCode.INITIALIZATION_NOT_ALLOWED, e.getErrorCode());
    }

    @Test
    void shouldRejectExpiredToken() {
        String tokenHash = InitializationTokenService.hashToken("expiredToken");
        InitializationToken dbToken = new InitializationToken();
        dbToken.setId(1L);
        dbToken.setTokenHash(tokenHash);
        dbToken.setConsumed(false);
        dbToken.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(tokenMapper.countConsumedTokens()).thenReturn(0L);
        when(tokenMapper.findByTokenHash(tokenHash)).thenReturn(Optional.of(dbToken));

        BusinessException e = assertThrows(BusinessException.class,
                () -> tokenService.consumeToken("expiredToken"));
        assertEquals(ErrorCode.INITIALIZATION_NOT_ALLOWED, e.getErrorCode());
    }

    @Test
    void shouldHandleConcurrentConsumption() {
        String tokenHash = InitializationTokenService.hashToken("concurrentToken");
        InitializationToken dbToken = new InitializationToken();
        dbToken.setId(1L);
        dbToken.setTokenHash(tokenHash);
        dbToken.setConsumed(false);
        dbToken.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(tokenMapper.countConsumedTokens()).thenReturn(0L);
        when(tokenMapper.findByTokenHash(tokenHash)).thenReturn(Optional.of(dbToken));
        // Simulate concurrent consumption: update returns 0 rows affected
        when(tokenMapper.consumeToken(1L)).thenReturn(0);

        BusinessException e = assertThrows(BusinessException.class,
                () -> tokenService.consumeToken("concurrentToken"));
        assertEquals(ErrorCode.INITIALIZATION_NOT_ALLOWED, e.getErrorCode());
    }

    @Test
    void isInitializedShouldReturnTrueWhenConsumedExists() {
        when(tokenMapper.countConsumedTokens()).thenReturn(1L);
        assertTrue(tokenService.isInitialized());
    }

    @Test
    void isInitializedShouldReturnFalseWhenNoConsumed() {
        when(tokenMapper.countConsumedTokens()).thenReturn(0L);
        assertFalse(tokenService.isInitialized());
    }

    @Test
    void shouldHashTokenCorrectly() {
        String hash1 = InitializationTokenService.hashToken("test");
        String hash2 = InitializationTokenService.hashToken("test");
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());

        // Different tokens produce different hashes
        String hash3 = InitializationTokenService.hashToken("test2");
        assertNotEquals(hash1, hash3);
    }
}
