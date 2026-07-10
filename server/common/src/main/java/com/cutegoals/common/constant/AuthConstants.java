package com.cutegoals.common.constant;

/**
 * Authentication-related constants.
 */
public final class AuthConstants {

    private AuthConstants() {}

    /** Role constants */
    public static final String ROLE_INSTANCE_ADMIN = "INSTANCE_ADMIN";
    public static final String ROLE_PARENT = "PARENT";
    public static final String ROLE_CHILD = "CHILD";

    /** Account status */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    /** Family member status */
    public static final String MEMBER_ACTIVE = "ACTIVE";
    public static final String MEMBER_INACTIVE = "INACTIVE";

    /** Initialization token */
    public static final int INIT_TOKEN_VALIDITY_HOURS = 24;
    public static final int INIT_TOKEN_BYTE_LENGTH = 32;

    /** JWT */
    public static final int JWT_ACCESS_EXPIRY_MINUTES = 15;
    public static final int REFRESH_TOKEN_EXPIRY_DAYS = 7;

    /** Session */
    public static final int SESSION_EXPIRY_DAYS = 30;

    /** Password */
    public static final int BCRYPT_STRENGTH = 12;

    /** Recovery token */
    public static final int RECOVERY_TOKEN_EXPIRY_MINUTES = 15;
    public static final int RECOVERY_TOKEN_BYTE_LENGTH = 32;

    /** Phone regex - China mainland mobile */
    public static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /** Request attribute names */
    public static final String ATTR_ACCOUNT_ID = "currentAccountId";
    public static final String ATTR_ROLES = "currentRoles";
    public static final String ATTR_SESSION_ID = "currentSessionId";
    public static final String ATTR_FAMILY_ID = "currentFamilyId";

    /** Cookie names */
    public static final String COOKIE_ACCESS_TOKEN = "access_token";
    public static final String COOKIE_REFRESH_TOKEN = "refresh_token";
    public static final String COOKIE_CSRF_TOKEN = "csrf_token";

    /** Header names */
    public static final String HEADER_CSRF = "X-CSRF-Token";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
}
