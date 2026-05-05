package com.ai.scheduler.dto.calendar.google_calendar;

import java.time.OffsetDateTime;
import java.util.List;

public record CalendarEventResponse(
        String id,
        String title,
        String description,
        String colorId,
        OffsetDateTime startDateTime,
        OffsetDateTime endDateTime,
        String timeZone,
        String htmlLink,
        List<String> recurrence
) {
}
