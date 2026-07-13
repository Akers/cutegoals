package com.cutegoals.auth.bootstrap;

import com.cutegoals.auth.mapper.InitializationTokenMapper;
import com.cutegoals.auth.service.InitializationTokenService;
import com.cutegoals.common.entity.auth.InitializationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InitializationBootstrap covering all four branches.
 */
@ExtendWith(MockitoExtension.class)
class InitializationBootstrapTest {

    @Mock
    private InitializationTokenMapper tokenMapper;

    @Mock
    private InitializationTokenService tokenService;

    private InitializationBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new InitializationBootstrap(tokenMapper, tokenService);
    }

    @Test
    void shouldSkipWhenTableNotEmpty() {
        // Table has 5 existing records
        when(tokenMapper.selectCount(null)).thenReturn(5L);

        bootstrap.run(null);

        verify(tokenMapper, never()).insert(any(InitializationToken.class));
        verify(tokenService, never()).generateToken();
    }

    @Test
    void shouldInsertFromInitTokenWhenTableEmpty() {
        String initTokenValue = "my-secret-init-token";
        String expectedHash = InitializationTokenService.hashToken(initTokenValue);

        when(tokenMapper.selectCount(null)).thenReturn(0L);
        ReflectionTestUtils.setField(bootstrap, "initToken", initTokenValue);
        ReflectionTestUtils.setField(bootstrap, "production", false);

        bootstrap.run(null);

        ArgumentCaptor<InitializationToken> captor = ArgumentCaptor.forClass(InitializationToken.class);
        verify(tokenMapper).insert(captor.capture());
        InitializationToken entity = captor.getValue();
        assertEquals(expectedHash, entity.getTokenHash());
        assertFalse(entity.getConsumed());
        assertNotNull(entity.getExpiresAt());
        verify(tokenService, never()).generateToken();
    }

    @Test
    void shouldGenerateTokenInDevModeWhenNoInitToken() {
        when(tokenMapper.selectCount(null)).thenReturn(0L);
        when(tokenService.generateToken()).thenReturn("dev-auto-generated-token");
        ReflectionTestUtils.setField(bootstrap, "initToken", "");
        ReflectionTestUtils.setField(bootstrap, "production", false);

        bootstrap.run(null);

        verify(tokenMapper, never()).insert(any(InitializationToken.class));
        verify(tokenService).generateToken();
    }

    @Test
    void shouldLogErrorInProductionWhenNoInitToken() {
        when(tokenMapper.selectCount(null)).thenReturn(0L);
        ReflectionTestUtils.setField(bootstrap, "initToken", "");
        ReflectionTestUtils.setField(bootstrap, "production", true);

        bootstrap.run(null);

        verify(tokenMapper, never()).insert(any(InitializationToken.class));
        verify(tokenService, never()).generateToken();
    }
}
