package com.cutegoals.auth.service;

/**
 * Audit event type constants for authentication-related events.
 */
public final class AuditEvent {

    private AuditEvent() {}

    public static final String INITIALIZE = "INITIALIZE";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String PASSWORD_CHANGE_FAILED = "PASSWORD_CHANGE_FAILED";
    public static final String RECOVERY_INITIATED = "RECOVERY_INITIATED";
    public static final String RECOVERY_SUCCESS = "RECOVERY_SUCCESS";
    public static final String RECOVERY_FAILED = "RECOVERY_FAILED";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
}
