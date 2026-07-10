package com.cutegoals.common.util;

/**
 * Utility for masking sensitive fields before logging or serialization.
 *
 * <p>All type-specific mask methods ({@link #maskPassword}, {@link #maskPin},
 * {@link #maskToken}, {@link #maskPhone}) delegate to {@link #mask} and
 * therefore share its null/empty pass-through contract:
 * <ul>
 *   <li>{@code null} input returns {@code null}
 *   <li>empty string input returns empty string
 *   <li>non-empty input returns {@code ***MASKED***}
 * </ul>
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
     * @param password the password to mask; if null or empty, returned as-is
     * @return {@code ***MASKED***} for non-empty input, or the input itself
     */
    public static String maskPassword(String password) {
        return mask(password);
    }

    /**
     * Masks a PIN value.
     *
     * @param pin the PIN to mask; if null or empty, returned as-is
     * @return {@code ***MASKED***} for non-empty input, or the input itself
     */
    public static String maskPin(String pin) {
        return mask(pin);
    }

    /**
     * Masks a token value.
     *
     * @param token the token to mask; if null or empty, returned as-is
     * @return {@code ***MASKED***} for non-empty input, or the input itself
     */
    public static String maskToken(String token) {
        return mask(token);
    }

    /**
     * Masks a phone number.
     *
     * @param phone the phone number to mask; if null or empty, returned as-is
     * @return {@code ***MASKED***} for non-empty input, or the input itself
     */
    public static String maskPhone(String phone) {
        return mask(phone);
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
