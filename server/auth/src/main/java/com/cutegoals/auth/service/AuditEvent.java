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

    // === Family events ===
    public static final String FAMILY_UPDATED = "FAMILY_UPDATED";
    public static final String INVITATION_CREATED = "INVITATION_CREATED";
    public static final String INVITATION_ACCEPTED = "INVITATION_ACCEPTED";
    public static final String INVITATION_REJECTED = "INVITATION_REJECTED";
    public static final String INVITATION_REVOKED = "INVITATION_REVOKED";
    public static final String CHILD_CREATED = "CHILD_CREATED";
    public static final String CHILD_UPDATED = "CHILD_UPDATED";
    public static final String CHILD_DELETED = "CHILD_DELETED";
    public static final String CHILD_DISABLED = "CHILD_DISABLED";
    public static final String PIN_SET = "PIN_SET";
    public static final String PIN_RESET = "PIN_RESET";
    public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
    public static final String MEMBER_LEFT = "MEMBER_LEFT";
    public static final String DEVICE_BOUND = "DEVICE_BOUND";
    public static final String DEVICE_REVOKED = "DEVICE_REVOKED";
    public static final String CHILD_LOGIN_SUCCESS = "CHILD_LOGIN_SUCCESS";
    public static final String CHILD_LOGIN_FAILED = "CHILD_LOGIN_FAILED";
    public static final String PIN_LOCKED_EVENT = "PIN_LOCKED";

    // === Task Template events ===
    public static final String TEMPLATE_CREATED = "TEMPLATE_CREATED";
    public static final String TEMPLATE_UPDATED = "TEMPLATE_UPDATED";
    public static final String TEMPLATE_DELETED = "TEMPLATE_DELETED";
    public static final String TEMPLATE_ENABLED = "TEMPLATE_ENABLED";
    public static final String TEMPLATE_DISABLED = "TEMPLATE_DISABLED";

    // === Task Assignment events ===
    public static final String ASSIGNMENT_CREATED = "ASSIGNMENT_CREATED";
    public static final String ASSIGNMENT_BATCH_CREATED = "ASSIGNMENT_BATCH_CREATED";
    public static final String ASSIGNMENT_GENERATED = "ASSIGNMENT_GENERATED";
    public static final String ASSIGNMENT_UPDATED = "ASSIGNMENT_UPDATED";
    public static final String ASSIGNMENT_CANCELLED = "ASSIGNMENT_CANCELLED";
    public static final String ASSIGNMENT_LATE_POLICY_OVERRIDE = "ASSIGNMENT_LATE_POLICY_OVERRIDE";
}
