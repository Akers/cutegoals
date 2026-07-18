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
 *
 * <p>{@link #maskPhonePartial} is a separate contract for display scenarios
 * (e.g. account management list) where operators need limited identifiability:
 * length &gt;= 7 yields {@code 136*****249}-style partial masks, while shorter
 * inputs fall back to {@link #MASKED}. Logging and audit code SHOULD continue
 * to use {@link #maskPhone} for defense-in-depth (full mask).
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
     * Partially masks a phone number for display scenarios where operators need
     * limited identifiability (e.g. account management list). For an 11-digit
     * Chinese mobile number this returns {@code 136*****249}.
     *
     * <p>Contract:
     * <ul>
     *   <li>{@code null} or empty input: returned as-is (consistent with {@link #maskPhone})
     *   <li>length &lt; 7: returns {@link #MASKED} (avoids leaking too much of short IDs)
     *   <li>length &gt;= 7: returns {@code prefix(3) + '*' × (len-6) + suffix(3)}
     * </ul>
     *
     * <p><strong>Not for logging.</strong> Logging and audit code should keep
     * using {@link #maskPhone} for full masking (defense-in-depth).
     *
     * @param phone the phone number to partially mask; if null or empty, returned as-is
     * @return partial mask like {@code 136*****249}, {@link #MASKED} for short input,
     *         or the input itself for null/empty
     */
    public static String maskPhonePartial(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        if (phone.length() < 7) {
            return MASKED;
        }
        // Unified rule: prefix(3) + '****' (4 stars) + suffix(4) for 11-digit mobile numbers.
        // Keeps identifiable tail (last 4) consistent with front-end display and admin bar.
        StringBuilder sb = new StringBuilder(phone.length());
        sb.append(phone, 0, 3);
        sb.append("****");
        sb.append(phone, phone.length() - 4, phone.length());
        return sb.toString();
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
