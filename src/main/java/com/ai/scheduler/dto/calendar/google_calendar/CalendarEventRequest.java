package com.ai.scheduler.dto.calendar.google_calendar;

import com.ai.scheduler.dto.calendar.CalendarColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CalendarEventRequest(
        @NotBlank String title,
        String description,
        CalendarColor color,
        @NotNull OffsetDateTime startDateTime,
        @NotNull OffsetDateTime endDateTime,
        String timeZone
) {
}
