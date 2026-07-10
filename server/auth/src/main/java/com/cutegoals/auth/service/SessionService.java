package com.cutegoals.auth.service;

import com.cutegoals.auth.mapper.RefreshTokenMapper;
import com.cutegoals.auth.mapper.SessionMapper;
import com.cutegoals.common.entity.auth.RefreshToken;
import com.cutegoals.common.entity.auth.Session;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service managing sessions and their revocation.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionMapper sessionMapper;
    private final RefreshTokenMapper refreshTokenMapper;

    /**
     * Create a new session for an account.
     *
     * @param accountId the account ID
     * @param deviceFingerprint optional device identifier
     * @return the generated session ID
     */
    @Transactional
    public String createSession(Long accountId, String deviceFingerprint) {
        String sessionId = UUID.randomUUID().toString();

        Session session = new Session();
        session.setSessionId(sessionId);
        session.setAccountId(accountId);
        session.setExpiresAt(LocalDateTime.now().plusDays(30));
        session.setDeviceFingerprint(deviceFingerprint);
        session.setRevoked(false);
        sessionMapper.insert(session);

        return sessionId;
    }

    /**
     * Revoke a single session.
     */
    @Transactional
    public void revokeSession(String sessionId) {
        sessionMapper.revokeSession(sessionId);
        refreshTokenMapper.revokeBySessionId(sessionId);
        log.info("Revoked session: {}", sessionId);
    }

    /**
     * Revoke all active sessions for an account.
     */
    @Transactional
    public void revokeAllSessions(Long accountId) {
        sessionMapper.revokeAllByAccountId(accountId);
        log.info("Revoked all sessions for accountId={}", accountId);
    }
}
