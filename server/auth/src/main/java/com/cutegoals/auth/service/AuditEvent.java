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

    // === Task Review events ===
    public static final String TASK_SUBMITTED = "TASK_SUBMITTED";
    public static final String TASK_REVIEW_APPROVED = "TASK_REVIEW_APPROVED";
    public static final String TASK_REVIEW_REJECTED = "TASK_REVIEW_REJECTED";

    // === Points events ===
    public static final String POINTS_EARN = "POINTS_EARN";
    public static final String POINTS_SPEND = "POINTS_SPEND";
    public static final String POINTS_REFUND = "POINTS_REFUND";
    public static final String POINTS_ADJUST = "POINTS_ADJUST";

    // === Prize events ===
    public static final String PRIZE_CREATED = "PRIZE_CREATED";
    public static final String PRIZE_UPDATED = "PRIZE_UPDATED";
    public static final String PRIZE_DELETED = "PRIZE_DELETED";
    public static final String PRIZE_STOCK_ADJUSTED = "PRIZE_STOCK_ADJUSTED";

    // === Blind Box events ===
    public static final String BLIND_BOX_POOL_CREATED = "BLIND_BOX_POOL_CREATED";
    public static final String BLIND_BOX_POOL_UPDATED = "BLIND_BOX_POOL_UPDATED";
    public static final String BLIND_BOX_POOL_DELETED = "BLIND_BOX_POOL_DELETED";
    public static final String BLIND_BOX_ITEM_ADDED = "BLIND_BOX_ITEM_ADDED";
    public static final String BLIND_BOX_ITEM_REMOVED = "BLIND_BOX_ITEM_REMOVED";

    // === Exchange events ===
    public static final String EXCHANGE_CREATED = "EXCHANGE_CREATED";
    public static final String EXCHANGE_DIRECT = "EXCHANGE_DIRECT";
    public static final String EXCHANGE_BLIND_BOX = "EXCHANGE_BLIND_BOX";
    public static final String EXCHANGE_FULFILLED = "EXCHANGE_FULFILLED";
    public static final String EXCHANGE_CANCELLED = "EXCHANGE_CANCELLED";

    // === Instance Management events ===
    public static final String CONFIG_CHANGED = "CONFIG_CHANGED";
    public static final String ACCOUNT_ENABLED = "ACCOUNT_ENABLED";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String FAMILY_EXPORTED = "FAMILY_EXPORTED";
}
