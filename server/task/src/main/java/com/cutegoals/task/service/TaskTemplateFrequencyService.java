package com.cutegoals.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * Service for computing the next trigger date from a task template's type_config.
 * <p>
 * The type_config JSON structure:
 * <pre>
 * {
 *   "frequency": "DAILY|WEEKLY|MONTHLY|YEARLY|NONE",
 *   "trigger_day": {
 *     "weekday": null,    // WEEKLY: ISO 1-7 (Mon=1, Sun=7)
 *     "mode": null,       // MONTHLY: "FIRST_DAY"|"LAST_DAY"|"MID_MONTH"
 *     "month": null,      // YEARLY: 1-12
 *     "day": null         // MONTHLY/YEARLY: 1-31, month-end adaptive
 *   }
 * }
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class TaskTemplateFrequencyService {

    private static final Logger log = LoggerFactory.getLogger(TaskTemplateFrequencyService.class);

    private final ObjectMapper objectMapper;

    /**
     * Compute the next trigger date on or after {@code fromDate} based on the given type_config.
     *
     * @param typeConfig JSON string with frequency and trigger_day, may be null/empty
     * @param fromDate   the reference date (exclusive — next trigger must be strictly after)
     * @return the next trigger date, or empty if no valid frequency/configuration
     */
    public Optional<LocalDate> nextTriggerDate(String typeConfig, LocalDate fromDate) {
        if (typeConfig == null || typeConfig.isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> config;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(typeConfig, Map.class);
            config = parsed;
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse type_config JSON: {}", e.getMessage());
            return Optional.empty();
        }

        String frequency = config.containsKey("frequency")
                ? String.valueOf(config.get("frequency"))
                : null;
        if (frequency == null || frequency.isEmpty() || "NONE".equals(frequency)) {
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> triggerDay = config.containsKey("trigger_day")
                ? (Map<String, Object>) config.get("trigger_day")
                : null;

        return switch (frequency) {
            case "DAILY" -> computeDaily(fromDate);
            case "WEEKLY" -> computeWeekly(fromDate, triggerDay);
            case "MONTHLY" -> computeMonthly(fromDate, triggerDay);
            case "YEARLY" -> computeYearly(fromDate, triggerDay);
            default -> {
                log.debug("Unknown frequency: {}", frequency);
                yield Optional.empty();
            }
        };
    }

    // ========== DAILY ==========

    private Optional<LocalDate> computeDaily(LocalDate fromDate) {
        return Optional.of(fromDate.plusDays(1));
    }

    // ========== WEEKLY ==========

    private Optional<LocalDate> computeWeekly(LocalDate fromDate, Map<String, Object> triggerDay) {
        if (triggerDay == null || !triggerDay.containsKey("weekday")) {
            return Optional.empty();
        }
        int targetWeekday;
        try {
            targetWeekday = ((Number) triggerDay.get("weekday")).intValue();
        } catch (ClassCastException | NullPointerException e) {
            return Optional.empty();
        }
        if (targetWeekday < 1 || targetWeekday > 7) {
            return Optional.empty();
        }

        int currentWeekday = fromDate.getDayOfWeek().getValue(); // Mon=1, Sun=7 (ISO)
        int daysUntilTarget = (targetWeekday - currentWeekday + 7) % 7;
        if (daysUntilTarget == 0) {
            // Today is the trigger day — advance to next week
            daysUntilTarget = 7;
        }
        return Optional.of(fromDate.plusDays(daysUntilTarget));
    }

    // ========== MONTHLY ==========

    private Optional<LocalDate> computeMonthly(LocalDate fromDate, Map<String, Object> triggerDay) {
        if (triggerDay == null) {
            return Optional.empty();
        }

        // Priority: mode (FIRST_DAY/LAST_DAY/MID_MONTH) over explicit day
        String mode = triggerDay.containsKey("mode")
                ? String.valueOf(triggerDay.get("mode"))
                : null;

        if (mode != null) {
            return switch (mode) {
                case "FIRST_DAY" -> {
                    LocalDate firstOfCurrentMonth = fromDate.withDayOfMonth(1);
                    if (fromDate.isBefore(firstOfCurrentMonth)) {
                        yield Optional.of(firstOfCurrentMonth);
                    }
                    yield Optional.of(firstOfCurrentMonth.plusMonths(1));
                }
                case "LAST_DAY" -> {
                    LocalDate lastOfCurrentMonth = fromDate.withDayOfMonth(fromDate.lengthOfMonth());
                    if (fromDate.isBefore(lastOfCurrentMonth)) {
                        yield Optional.of(lastOfCurrentMonth);
                    }
                    yield Optional.of(lastOfCurrentMonth.plusMonths(1).withDayOfMonth(
                            lastOfCurrentMonth.plusMonths(1).lengthOfMonth()));
                }
                case "MID_MONTH" -> {
                    LocalDate midOfCurrentMonth = fromDate.withDayOfMonth(15);
                    if (fromDate.isBefore(midOfCurrentMonth)) {
                        yield Optional.of(midOfCurrentMonth);
                    }
                    yield Optional.of(midOfCurrentMonth.plusMonths(1));
                }
                default -> Optional.empty();
            };
        }

        // Fallback: explicit day field
        if (!triggerDay.containsKey("day")) {
            return Optional.empty();
        }
        int targetDay;
        try {
            targetDay = ((Number) triggerDay.get("day")).intValue();
        } catch (ClassCastException | NullPointerException e) {
            return Optional.empty();
        }
        if (targetDay < 1 || targetDay > 31) {
            return Optional.empty();
        }

        // Try current month first
        Optional<LocalDate> currentMonth = safeWithDayOfMonth(fromDate, targetDay);
        if (currentMonth.isPresent() && (currentMonth.get().isAfter(fromDate) || currentMonth.get().isEqual(fromDate))) {
            // Same day as fromDate — advance to next month
            if (currentMonth.get().isEqual(fromDate)) {
                return safeWithDayOfMonth(fromDate.plusMonths(1), targetDay);
            }
            return currentMonth;
        }

        // Try next month
        return safeWithDayOfMonth(fromDate.plusMonths(1), targetDay);
    }

    // ========== YEARLY ==========

    private Optional<LocalDate> computeYearly(LocalDate fromDate, Map<String, Object> triggerDay) {
        if (triggerDay == null || !triggerDay.containsKey("month") || !triggerDay.containsKey("day")) {
            return Optional.empty();
        }

        int targetMonth;
        int targetDay;
        try {
            targetMonth = ((Number) triggerDay.get("month")).intValue();
            targetDay = ((Number) triggerDay.get("day")).intValue();
        } catch (ClassCastException | NullPointerException e) {
            return Optional.empty();
        }

        if (targetMonth < 1 || targetMonth > 12 || targetDay < 1 || targetDay > 31) {
            return Optional.empty();
        }

        boolean isFeb29 = (targetMonth == 2 && targetDay == 29);

        // Try current year: exact date only; for non-Feb-29, fall back to adapted
        LocalDate candidate = tryExactDate(fromDate.getYear(), targetMonth, targetDay);
        if (candidate != null && candidate.isAfter(fromDate)) {
            return Optional.of(candidate);
        }
        // Non-Feb-29: try adapted (e.g., Apr 31 → Apr 30)
        if (!isFeb29) {
            LocalDate adapted = tryAdaptedDate(fromDate.getYear(), targetMonth, targetDay);
            if (adapted != null && adapted.isAfter(fromDate)) {
                return Optional.of(adapted);
            }
        }

        // Try subsequent years
        int year = fromDate.getYear() + 1;
        int maxIterations = 10;
        for (int i = 0; i < maxIterations; i++) {
            LocalDate exact = tryExactDate(year, targetMonth, targetDay);
            if (exact != null) {
                return Optional.of(exact);
            }
            // Non-Feb-29: try adapted
            if (!isFeb29) {
                LocalDate adapted = tryAdaptedDate(year, targetMonth, targetDay);
                if (adapted != null) {
                    return Optional.of(adapted);
                }
            }
            year++;
        }

        return Optional.empty();
    }

    // ========== Helpers ==========

    /**
     * Safely create a LocalDate with a given day-of-month, adapting down if the month
     * doesn't have enough days (e.g., Feb 30 → Feb 28/29).
     */
    private Optional<LocalDate> safeWithDayOfMonth(LocalDate base, int dayOfMonth) {
        int maxDay = base.lengthOfMonth();
        int adjustedDay = Math.min(dayOfMonth, maxDay);
        try {
            return Optional.of(base.withDayOfMonth(adjustedDay));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Try to create an exact date. Returns null if the date does not exist
     * (e.g., Feb 29 in a non-leap year, or Apr 31).
     */
    private LocalDate tryExactDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /**
     * Try to create an adapted date by clamping the day to the month's maximum.
     * Returns null only on unexpected errors (should rarely fail since clamping
     * guarantees validity).
     */
    private LocalDate tryAdaptedDate(int year, int month, int day) {
        try {
            int maxDay = LocalDate.of(year, month, 1).lengthOfMonth();
            int adjustedDay = Math.min(day, maxDay);
            return LocalDate.of(year, month, adjustedDay);
        } catch (DateTimeException e) {
            return null;
        }
    }
}
