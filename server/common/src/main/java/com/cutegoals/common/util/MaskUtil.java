package com.cutegoals.common.util;

/**
 * Utility for masking sensitive fields before logging or serialization.
 *
 * All mask methods return the constant {@code ***MASKED***} to prevent
 * accidental leakage of passwords, PINs, tokens, or phone numbers.
 */
public final class MaskUtil {

    /** The constant string used to replace sensitive values. */
    public static final String MASKED = "***MASKED***";

    private MaskUtil() {
        // utility class — prevent instantiation
    }

    /**
     * Masks a password value.
     *
     * @param password the password to mask (value ignored)
     * @return {@code ***MASKED***}
     */
    public static String maskPassword(String password) {
        return MASKED;
    }

    /**
     * Masks a PIN value.
     *
     * @param pin the PIN to mask (value ignored)
     * @return {@code ***MASKED***}
     */
    public static String maskPin(String pin) {
        return MASKED;
    }

    /**
     * Masks a token value.
     *
     * @param token the token to mask (value ignored)
     * @return {@code ***MASKED***}
     */
    public static String maskToken(String token) {
        return MASKED;
    }

    /**
     * Masks a phone number.
     *
     * @param phone the phone number to mask (value ignored)
     * @return {@code ***MASKED***}
     */
    public static String maskPhone(String phone) {
        return MASKED;
    }

    /**
     * Masks an arbitrary sensitive string.
     *
     * @param sensitive the sensitive value; if null or empty, returned as-is
     * @return {@code ***MASKED***} for non-empty input, or the input itself
     */
    public static String mask(String sensitive) {
        if (sensitive == null || sensitive.isEmpty()) {
            return sensitive;
        }
        return MASKED;
    }
}
