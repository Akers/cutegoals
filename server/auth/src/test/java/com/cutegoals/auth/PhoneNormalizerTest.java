package com.cutegoals.auth;

import com.cutegoals.auth.service.PhoneNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PhoneNormalizer.
 */
class PhoneNormalizerTest {

    @Test
    void shouldNormalizeStandardPhone() {
        assertEquals("13800138000", PhoneNormalizer.normalize("13800138000"));
    }

    @Test
    void shouldStripNonDigits() {
        assertEquals("13800138000", PhoneNormalizer.normalize("138-0013-8000"));
    }

    @Test
    void shouldStripSpaces() {
        assertEquals("13800138000", PhoneNormalizer.normalize("138 0013 8000"));
    }

    @Test
    void shouldHandle86Prefix() {
        assertEquals("13800138000", PhoneNormalizer.normalize("8613800138000"));
    }

    @Test
    void shouldHandlePlus86Prefix() {
        assertEquals("13800138000", PhoneNormalizer.normalize("+8613800138000"));
    }

    @Test
    void shouldRejectNullInput() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNormalizer.normalize(null));
    }

    @Test
    void shouldRejectEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNormalizer.normalize(""));
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNormalizer.normalize("12345"));
    }

    @Test
    void shouldRejectNonMobilePrefix() {
        assertThrows(IllegalArgumentException.class, () -> PhoneNormalizer.normalize("10000000000"));
    }
}
