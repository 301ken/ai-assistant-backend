package com.ai.scheduler.dto.activity;

/**
 * Planned vs actual time breakdown per calendar event title within a date range.
 *
 * @param eventTitle     The title of the calendar event (grouped across all occurrences).
 * @param plannedSeconds Total seconds scheduled in Google Calendar for this title.
 * @param actualSeconds  Total seconds spent in linked activity sessions for this title.
 * @param deficitSeconds actualSeconds - plannedSeconds (negative = did less than planned).
 */
public record EventComparisonResponse(
        String eventTitle,
        long plannedSeconds,
        long actualSeconds,
        long deficitSeconds
) {
}
