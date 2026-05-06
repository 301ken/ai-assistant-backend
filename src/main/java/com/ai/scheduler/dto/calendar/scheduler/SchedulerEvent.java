package com.ai.scheduler.dto.calendar.scheduler;

import com.ai.scheduler.dto.calendar.CalendarColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record SchedulerEvent(
        @NotBlank String title,
        String description,
        CalendarColor color,
        @NotNull OffsetDateTime startDateTime,
        @NotNull OffsetDateTime endDateTime,
        String timeZone,
        List<String> recurrence
) {
}
