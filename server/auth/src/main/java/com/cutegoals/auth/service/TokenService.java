package com.cutegoals.auth.service;

import com.cutegoals.auth.mapper.RefreshTokenMapper;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.auth.RefreshToken;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * JWT access token and refresh token management.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenMapper refreshTokenMapper;

    @Value("${app.auth.jwt.secret:changeit-changeit-changeit-changeit-changeit}")
    private String jwtSecret;

    @Value("${app.auth.jwt.access-expiry-minutes:15}")
    private int accessExpiryMinutes;

    @Value("${app.auth.jwt.refresh-expiry-days:7}")
    private int refreshExpiryDays;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        // Ensure key is at least 256 bits for HS256
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Pad key to 32 bytes
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate an access token (JWT).
     */
    public String generateAccessToken(Long accountId, List<String> roles, String sessionId) {
        Date now = new Date();
        Date expiry = Date.from(
                LocalDateTime.now().plusMinutes(accessExpiryMinutes)
                        .atZone(ZoneId.systemDefault()).toInstant()
        );

        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .issuer("cute-goals")
                .issuedAt(now)
                .expiration(expiry)
                .claim("roles", roles)
                .claim("sid", sessionId)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a refresh token (opaque, stored as hash in DB).
     *
     * @param sessionId the session ID
     * @return the plaintext refresh token
     */
    @Transactional
    public String generateRefreshToken(String sessionId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String plainToken = HexFormat.of().formatHex(bytes);

        String familyId = UUID.randomUUID().toString();

        RefreshToken entity = new RefreshToken();
        entity.setTokenHash(hashToken(plainToken));
        entity.setSessionId(sessionId);
        entity.setFamilyId(familyId);
        entity.setExpiresAt(LocalDateTime.now().plusDays(refreshExpiryDays));
        entity.setRevoked(false);
        refreshTokenMapper.insert(entity);

        return plainToken;
    }

    /**
     * Refresh token rotation: validate old refresh token, issue new pair, revoke old.
     *
     * @param oldPlainToken the current refresh token plaintext
     * @return new access token and refresh token
     * @throws BusinessException if token is invalid/expired/reused
     */
    @Transactional
    public TokenRefreshResult refreshTokens(String oldPlainToken) {
        String tokenHash = hashToken(oldPlainToken);
        RefreshToken token = refreshTokenMapper.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (token.getRevoked()) {
            // Token was already used → detect reuse attack
            String familyId = token.getFamilyId();
            if (familyId != null) {
                refreshTokenMapper.revokeFamily(familyId);
                log.warn("Refresh token reuse detected! Revoked entire family chain: {}", familyId);
            }
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // Revoke old token
        refreshTokenMapper.revokeById(token.getId());

        // Generate new access token (we need the session to get roles - caller handles this)
        String sessionId = token.getSessionId();
        String newRefreshPlain = generateRefreshToken(sessionId);

        log.info("Refreshed tokens for sessionId={}", sessionId);

        return new TokenRefreshResult(sessionId, newRefreshPlain);
    }

    /**
     * Parse and validate an access token.
     *
     * @param accessToken JWT string
     * @return the claims if valid
     * @throws BusinessException if token is invalid/expired
     */
    public JwtClaims parseAccessToken(String accessToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();

            Long accountId = Long.valueOf(claims.getSubject());
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get("roles");
            String sessionId = claims.get("sid", String.class);

            return new JwtClaims(accountId, roles, sessionId);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * Revoke all refresh tokens for a session.
     */
    @Transactional
    public void revokeBySessionId(String sessionId) {
        refreshTokenMapper.revokeBySessionId(sessionId);
    }

    /**
     * Revoke all refresh tokens in a family chain.
     */
    @Transactional
    public void revokeFamily(String familyId) {
        refreshTokenMapper.revokeFamily(familyId);
    }

    /**
     * Hash a token using SHA-256.
     */
    public static String hashToken(String plainToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }

    /**
     * Result of token refresh.
     */
    public record TokenRefreshResult(String sessionId, String newRefreshToken) {}

    /**
     * Parsed JWT claims.
     */
    public record JwtClaims(Long accountId, List<String> roles, String sessionId) {}
}
