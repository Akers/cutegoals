package com.cutegoals.auth.service;

import com.cutegoals.auth.mapper.InitializationTokenMapper;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.auth.InitializationToken;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Service for generating, validating and consuming initialization tokens.
 * Logs are masked to never reveal the token plaintext.
 */
@Service
@RequiredArgsConstructor
public class InitializationTokenService {

    private static final Logger log = LoggerFactory.getLogger(InitializationTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InitializationTokenMapper tokenMapper;

    /**
     * Generate a new initialization token, persist its hash, and return the plaintext.
     * The plaintext is never logged.
     */
    @Transactional
    public String generateToken() {
        byte[] bytes = new byte[AuthConstants.INIT_TOKEN_BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        String plainToken = HexFormat.of().formatHex(bytes);

        String tokenHash = hashToken(plainToken);
        InitializationToken entity = new InitializationToken();
        entity.setTokenHash(tokenHash);
        entity.setConsumed(false);
        entity.setExpiresAt(LocalDateTime.now().plusHours(AuthConstants.INIT_TOKEN_VALIDITY_HOURS));
        tokenMapper.insert(entity);

        log.info("Generated initialization token id={}", entity.getId());
        return plainToken;
    }

    /**
     * Consume a token atomically. Ensures single-use even under concurrency.
     *
     * @param plainToken the plaintext token from the request
     * @throws BusinessException if token is invalid, consumed, expired, or instance already initialized
     */
    @Transactional
    public void consumeToken(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            throw new BusinessException(ErrorCode.INITIALIZATION_NOT_ALLOWED);
        }

        // Check instance not already initialized
        if (isInitialized()) {
            throw new BusinessException(ErrorCode.INITIALIZATION_NOT_ALLOWED);
        }

        String tokenHash = hashToken(plainToken);
        InitializationToken token = tokenMapper.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INITIALIZATION_NOT_ALLOWED));

        if (token.getConsumed()) {
            throw new BusinessException(ErrorCode.INITIALIZATION_NOT_ALLOWED);
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INITIALIZATION_NOT_ALLOWED);
        }

        // Atomic consume: update with optimistic locking (consumed=FALSE check)
        int updated = tokenMapper.consumeToken(token.getId());
        if (updated == 0) {
            // Concurrent consumption detected
            throw new BusinessException(ErrorCode.INITIALIZATION_NOT_ALLOWED);
        }

        log.info("Consumed initialization token id={}", token.getId());
    }

    /**
     * Check if any valid (consumed) token exists, meaning instance was initialized.
     */
    public boolean isInitialized() {
        return tokenMapper.countConsumedTokens() > 0;
    }

    /**
     * Check if there are any valid unconsumed tokens.
     */
    public boolean hasValidToken() {
        return tokenMapper.countValidTokens() > 0;
    }

    /**
     * Hash a token for storage comparison. Uses SHA-256.
     */
    public static String hashToken(String plainToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}
