package com.cutegoals.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MaskUtil — sensitive field masking.
 *
 * Covers:
 * - maskPassword returns ***MASKED***
 * - maskPin returns ***MASKED***
 * - maskToken returns ***MASKED***
 * - maskPhone returns ***MASKED***
 * - mask (generic) returns ***MASKED***
 * - Null/empty inputs pass through for generic mask
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
}
