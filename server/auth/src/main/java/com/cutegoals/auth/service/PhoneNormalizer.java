package com.cutegoals.auth.service;

import com.cutegoals.common.constant.AuthConstants;

/**
 * Utility for normalizing Chinese mainland mobile phone numbers.
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {}

    /**
     * Normalize a phone number: strip non-digit chars and validate format.
     *
     * @param raw raw phone input
     * @return normalized phone number (11 digits)
     * @throws IllegalArgumentException if invalid format
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Phone number must not be empty");
        }

        // Strip all non-digit characters
        String digits = raw.replaceAll("\\D+", "");

        // Handle optional +86 prefix
        if (digits.startsWith("86") && digits.length() == 13) {
            digits = digits.substring(2);
        }

        if (!digits.matches(AuthConstants.PHONE_REGEX)) {
            throw new IllegalArgumentException("Invalid phone number format");
        }

        return digits;
    }
}
