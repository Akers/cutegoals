package com.cutegoals.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MaskUtil — sensitive field masking.
 *
 * Covers:
 * - maskPassword returns ***MASKED*** (null/empty pass-through)
 * - maskPin returns ***MASKED*** (null/empty pass-through)
 * - maskToken returns ***MASKED*** (null/empty pass-through)
 * - maskPhone returns ***MASKED*** (null/empty pass-through)
 * - mask (generic) returns ***MASKED*** (null/empty pass-through)
 * - Null/empty inputs pass through for all methods
 * - Original values do not appear in masked output
 */
class MaskUtilTest {

    private static final String MASKED = "***MASKED***";

    // --- Specific mask methods ---

    @Test
    void maskPasswordReturnsMasked() {
        assertThat(MaskUtil.maskPassword("mySecretPass123")).isEqualTo(MASKED);
    }

    @Test
    void maskPinReturnsMasked() {
        assertThat(MaskUtil.maskPin("1234")).isEqualTo(MASKED);
    }

    @Test
    void maskTokenReturnsMasked() {
        assertThat(MaskUtil.maskToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0")).isEqualTo(MASKED);
    }

    @Test
    void maskPhoneReturnsMasked() {
        assertThat(MaskUtil.maskPhone("13800138000")).isEqualTo(MASKED);
    }

    // --- Null/empty pass-through for type-specific methods ---

    @ParameterizedTest
    @NullAndEmptySource
    void maskPasswordReturnsInputForNullOrEmpty(String input) {
        assertThat(MaskUtil.maskPassword(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void maskPinReturnsInputForNullOrEmpty(String input) {
        assertThat(MaskUtil.maskPin(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void maskTokenReturnsInputForNullOrEmpty(String input) {
        assertThat(MaskUtil.maskToken(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void maskPhoneReturnsInputForNullOrEmpty(String input) {
        assertThat(MaskUtil.maskPhone(input)).isEqualTo(input);
    }

    // --- Generic mask ---

    @Test
    void maskGenericReturnsMasked() {
        assertThat(MaskUtil.mask("sensitive-data")).isEqualTo(MASKED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void maskGenericReturnsInputForNullOrEmpty(String input) {
        assertThat(MaskUtil.mask(input)).isEqualTo(input);
    }

    // --- No original values leak ---

    @Test
    void maskPasswordContainsNoOriginalValue() {
        String password = "mySuperSecretPassword!";
        assertThat(MaskUtil.maskPassword(password))
            .doesNotContain(password)
            .isEqualTo(MASKED);
    }

    @Test
    void maskPinContainsNoOriginalValue() {
        String pin = "9876";
        assertThat(MaskUtil.maskPin(pin))
            .doesNotContain(pin)
            .isEqualTo(MASKED);
    }

    @Test
    void maskTokenContainsNoOriginalValue() {
        String token = "Bearer.some.jwt.token";
        assertThat(MaskUtil.maskToken(token))
            .doesNotContain(token)
            .isEqualTo(MASKED);
    }

    @Test
    void maskPhoneContainsNoOriginalValue() {
        String phone = "13912345678";
        assertThat(MaskUtil.maskPhone(phone))
            .doesNotContain(phone)
            .isEqualTo(MASKED);
    }

    // --- maskPhonePartial (display-grade partial masking) ---

    @Test
    void maskPhonePartialReturnsPartialMaskFor11Digits() {
        // Unified rule: 3 prefix + 4 stars + 4 suffix for 11-digit mobile numbers
        assertThat(MaskUtil.maskPhonePartial("13612341249")).isEqualTo("136****1249");
    }

    @Test
    void maskPhonePartialReturnsPartialMaskFor7Digits() {
        // 7 digits still gets prefix(3) + 4 stars + suffix(4)
        assertThat(MaskUtil.maskPhonePartial("1234567")).isEqualTo("123****4567");
    }

    @Test
    void maskPhonePartialReturnsMaskedFor6Digits() {
        // Below threshold 7: fall back to full mask to avoid short-ID leakage
        assertThat(MaskUtil.maskPhonePartial("123456")).isEqualTo(MASKED);
    }

    @Test
    void maskPhonePartialReturnsMaskedFor4DigitPin() {
        // Defensive: a 4-digit PIN-shaped value should not become "1**4"
        assertThat(MaskUtil.maskPhonePartial("1234")).isEqualTo(MASKED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void maskPhonePartialReturnsInputForNullOrEmpty(String input) {
        assertThat(MaskUtil.maskPhonePartial(input)).isEqualTo(input);
    }

    @Test
    void maskPhonePartialContainsNoFullOriginal() {
        // Partial mask preserves prefix/suffix but must not contain the full original
        String phone = "13612341249";
        assertThat(MaskUtil.maskPhonePartial(phone))
            .doesNotContain(phone);
    }
}
