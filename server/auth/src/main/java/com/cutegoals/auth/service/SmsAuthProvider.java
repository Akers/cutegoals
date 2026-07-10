package com.cutegoals.auth.service;

/**
 * SPI for SMS authentication providers.
 * Implementations are discovered via Spring's @Component scanning.
 */
public interface SmsAuthProvider {

    /**
     * Check if this provider is fully configured and enabled.
     */
    boolean isEnabled();

    /**
     * Send a verification code to the given phone number.
     *
     * @param phone normalized phone number
     * @throws UnsupportedOperationException if not enabled
     */
    void sendVerificationCode(String phone);

    /**
     * Verify a code submitted by the user.
     *
     * @param phone        normalized phone number
     * @param code         the code submitted by user
     * @return true if code is valid and not expired
     * @throws UnsupportedOperationException if not enabled
     */
    boolean verifyCode(String phone, String code);
}
