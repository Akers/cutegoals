package com.cutegoals.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaskTemplateFrequencyService.nextTriggerDate().
 * <p>
 * Covers: DAILY, WEEKLY, MONTHLY, YEARLY frequencies,
 * month-end adaption, leap-year handling, and null/invalid inputs.
 */
class TaskTemplateFrequencyServiceTest {

    private TaskTemplateFrequencyService service;

    @BeforeEach
    void setUp() {
        service = new TaskTemplateFrequencyService(new ObjectMapper());
    }

    // ========== NULL / NONE / invalid ==========

    @Test
    void shouldReturnEmptyForNullTypeConfig() {
        assertTrue(service.nextTriggerDate(null, LocalDate.of(2026, 7, 14)).isEmpty());
    }

    @Test
    void shouldReturnEmptyForEmptyTypeConfig() {
        assertTrue(service.nextTriggerDate("", LocalDate.of(2026, 7, 14)).isEmpty());
    }

    @Test
    void shouldReturnEmptyForNoneFrequency() {
        String config = jsonConfig("NONE", null);
        assertTrue(service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).isEmpty());
    }

    @Test
    void shouldReturnEmptyForInvalidJson() {
        assertTrue(service.nextTriggerDate("{invalid", LocalDate.of(2026, 7, 14)).isEmpty());
    }

    @Test
    void shouldReturnEmptyForMissingFrequency() {
        assertTrue(service.nextTriggerDate("{}", LocalDate.of(2026, 7, 14)).isEmpty());
    }

    // ========== DAILY ==========

    @Test
    void dailyShouldReturnNextDay() {
        String config = jsonConfig("DAILY", null);
        assertEquals(
                LocalDate.of(2026, 7, 15),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).orElseThrow()
        );
    }

    @Test
    void dailyShouldWorkAcrossMonthBoundary() {
        String config = jsonConfig("DAILY", null);
        assertEquals(
                LocalDate.of(2026, 8, 1),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 31)).orElseThrow()
        );
    }

    @Test
    void dailyShouldWorkAcrossYearBoundary() {
        String config = jsonConfig("DAILY", null);
        assertEquals(
                LocalDate.of(2027, 1, 1),
                service.nextTriggerDate(config, LocalDate.of(2026, 12, 31)).orElseThrow()
        );
    }

    // ========== WEEKLY ==========

    @Test
    void weeklyShouldReturnNextMonday() {
        // 2026-07-14 is Tuesday (ISO=2) → next Monday is 2026-07-20 (ISO=1)
        String config = jsonConfig("WEEKLY", Map.of("weekday", 1));
        assertEquals(
                LocalDate.of(2026, 7, 20),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).orElseThrow()
        );
    }

    @Test
    void weeklyWhenTodayIsTriggerDayShouldAdvanceToNextWeek() {
        // 2026-07-14 is Tuesday (ISO=2) → next Tuesday is 2026-07-21
        String config = jsonConfig("WEEKLY", Map.of("weekday", 2));
        assertEquals(
                LocalDate.of(2026, 7, 21),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).orElseThrow()
        );
    }

    @Test
    void weeklyWithSundayShouldWork() {
        // 2026-07-14 is Tuesday (ISO=2) → next Sunday (ISO=7) is 2026-07-19
        String config = jsonConfig("WEEKLY", Map.of("weekday", 7));
        assertEquals(
                LocalDate.of(2026, 7, 19),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).orElseThrow()
        );
    }

    @Test
    void weeklyShouldReturnEmptyForMissingWeekday() {
        String config = jsonConfig("WEEKLY", null);
        assertTrue(service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).isEmpty());
    }

    // ========== MONTHLY: FIRST_DAY ==========

    @Test
    void monthlyFirstDayShouldReturnFirstOfNextMonth() {
        String config = jsonConfig("MONTHLY", Map.of("mode", "FIRST_DAY"));
        assertEquals(
                LocalDate.of(2026, 8, 1),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).orElseThrow()
        );
    }

    @Test
    void monthlyFirstDayFromFirstOfMonthShouldReturnNextMonth() {
        String config = jsonConfig("MONTHLY", Map.of("mode", "FIRST_DAY"));
        assertEquals(
                LocalDate.of(2026, 8, 1),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 1)).orElseThrow()
        );
    }

    // ========== MONTHLY: LAST_DAY ==========

    @Test
    void monthlyLastDayShouldReturnLastDayOfCurrentMonth() {
        // July has 31 days → last day is 31
        String config = jsonConfig("MONTHLY", Map.of("mode", "LAST_DAY"));
        assertEquals(
                LocalDate.of(2026, 7, 31),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).orElseThrow()
        );
    }

    @Test
    void monthlyLastDayOnLastDayShouldReturnNextMonthLastDay() {
        // July 31 → next trigger is August 31
        String config = jsonConfig("MONTHLY", Map.of("mode", "LAST_DAY"));
        assertEquals(
                LocalDate.of(2026, 8, 31),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 31)).orElseThrow()
        );
    }

    @Test
    void monthlyLastDayFebShouldReturnFeb28NonLeap() {
        // 2025-01-31 (last day of Jan, so advance to next month) → last day of Feb 2025 is 28
        String config = jsonConfig("MONTHLY", Map.of("mode", "LAST_DAY"));
        assertEquals(
                LocalDate.of(2025, 2, 28),
                service.nextTriggerDate(config, LocalDate.of(2025, 1, 31)).orElseThrow()
        );
    }

    @Test
    void monthlyLastDayFebLeapShouldReturnFeb29() {
        // 2024-01-31 (last day of Jan) → last day of Feb 2024 is 29 (leap year)
        String config = jsonConfig("MONTHLY", Map.of("mode", "LAST_DAY"));
        assertEquals(
                LocalDate.of(2024, 2, 29),
                service.nextTriggerDate(config, LocalDate.of(2024, 1, 31)).orElseThrow()
        );
    }

    // ========== MONTHLY: MID_MONTH ==========

    @Test
    void monthlyMidMonthShouldReturn15th() {
        String config = jsonConfig("MONTHLY", Map.of("mode", "MID_MONTH"));
        assertEquals(
                LocalDate.of(2026, 7, 15),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 1)).orElseThrow()
        );
    }

    @Test
    void monthlyMidMonthPastMidShouldReturnNextMonth() {
        String config = jsonConfig("MONTHLY", Map.of("mode", "MID_MONTH"));
        assertEquals(
                LocalDate.of(2026, 8, 15),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 20)).orElseThrow()
        );
    }

    // ========== MONTHLY: Fixed day ==========

    @Test
    void monthlyWithDayShouldReturnSpecifiedDay() {
        // day=10, from July 14 → next month Aug 10
        String config = jsonConfig("MONTHLY", Map.of("day", 10));
        assertEquals(
                LocalDate.of(2026, 8, 10),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).orElseThrow()
        );
    }

    @Test
    void monthlyWithDayBeforeDateShouldReturnSameMonth() {
        // day=10, from July 5 → same month July 10
        String config = jsonConfig("MONTHLY", Map.of("day", 10));
        assertEquals(
                LocalDate.of(2026, 7, 10),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 5)).orElseThrow()
        );
    }

    @Test
    void monthlyWithDay30ShouldAdaptFebTo28() {
        // day=30, from Jan 31 2025 (Jan 30 already passed) → Feb doesn't have 30, should return Feb 28
        String config = jsonConfig("MONTHLY", Map.of("day", 30));
        assertEquals(
                LocalDate.of(2025, 2, 28),
                service.nextTriggerDate(config, LocalDate.of(2025, 1, 31)).orElseThrow()
        );
    }

    @Test
    void monthlyWithDay30ShouldAdaptFebLeapTo29() {
        // day=30, from Jan 31 2024 (Jan 30 already passed) → Feb 2024 is leap year, should return Feb 29
        String config = jsonConfig("MONTHLY", Map.of("day", 30));
        assertEquals(
                LocalDate.of(2024, 2, 29),
                service.nextTriggerDate(config, LocalDate.of(2024, 1, 31)).orElseThrow()
        );
    }

    @Test
    void monthlyShouldReturnEmptyForMissingDayAndMode() {
        String config = jsonConfig("MONTHLY", null);
        assertTrue(service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).isEmpty());
    }

    // ========== YEARLY ==========

    @Test
    void yearlyShouldReturnSameDayNextYear() {
        // month=12, day=25, from 2026-07-14 → 2026-12-25
        String config = jsonConfig("YEARLY", Map.of("month", 12, "day", 25));
        assertEquals(
                LocalDate.of(2026, 12, 25),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).orElseThrow()
        );
    }

    @Test
    void yearlyWhenDateAlreadyPassedShouldReturnNextYear() {
        // month=3, day=15, from 2026-07-14 (already past) → 2027-03-15
        String config = jsonConfig("YEARLY", Map.of("month", 3, "day", 15));
        assertEquals(
                LocalDate.of(2027, 3, 15),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).orElseThrow()
        );
    }

    @Test
    void yearlyOnExactDateShouldReturnNextYear() {
        // month=7, day=14, from 2026-07-14 → 2027-07-14
        String config = jsonConfig("YEARLY", Map.of("month", 7, "day", 14));
        assertEquals(
                LocalDate.of(2027, 7, 14),
                service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).orElseThrow()
        );
    }

    @Test
    void yearlyFeb29LeapYearShouldBeValidTrigger() {
        // 2024-02-29 exists (leap year), from 2023-06-15 → 2024-02-29
        String config = jsonConfig("YEARLY", Map.of("month", 2, "day", 29));
        assertEquals(
                LocalDate.of(2024, 2, 29),
                service.nextTriggerDate(config, LocalDate.of(2023, 6, 15)).orElseThrow()
        );
    }

    @Test
    void yearlyFeb29NonLeapYearShouldSkipToNextLeapYear() {
        // 2025-02-29 doesn't exist, from 2024-06-15 → should skip to 2028-02-29 (next leap year)
        String config = jsonConfig("YEARLY", Map.of("month", 2, "day", 29));
        assertEquals(
                LocalDate.of(2028, 2, 29),
                service.nextTriggerDate(config, LocalDate.of(2024, 6, 15)).orElseThrow()
        );
    }

    @Test
    void yearlyFeb29FromFeb28ShouldSkipToNextLeapYear() {
        // 2025-02-28 from, config for Feb 29 → 2028-02-29
        String config = jsonConfig("YEARLY", Map.of("month", 2, "day", 29));
        assertEquals(
                LocalDate.of(2028, 2, 29),
                service.nextTriggerDate(config, LocalDate.of(2025, 2, 28)).orElseThrow()
        );
    }

    @Test
    void yearlyWithDay31ShouldHandleShortMonths() {
        // day=31, month=4 (April has 30 days) → 2026-04-30 (adapted down)
        String config = jsonConfig("YEARLY", Map.of("month", 4, "day", 31));
        assertEquals(
                LocalDate.of(2026, 4, 30),
                service.nextTriggerDate(config, LocalDate.of(2026, 3, 15)).orElseThrow()
        );
    }

    @Test
    void yearlyShouldReturnEmptyForMissingMonth() {
        String config = jsonConfig("YEARLY", Map.of("day", 25));
        assertTrue(service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).isEmpty());
    }

    @Test
    void yearlyShouldReturnEmptyForMissingDay() {
        String config = jsonConfig("YEARLY", Map.of("month", 12));
        assertTrue(service.nextTriggerDate(config, LocalDate.of(2026, 7, 14)).isEmpty());
    }

    // ========== Helpers ==========

    private String jsonConfig(String frequency, Object triggerDay) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> config = new java.util.LinkedHashMap<>();
            config.put("frequency", frequency);
            if (triggerDay != null) {
                config.put("trigger_day", triggerDay);
            }
            return mapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
