package com.ai.scheduler.dto.calendar;

import java.time.OffsetDateTime;

public record ScheduleConstraint(
        OffsetDateTime start,
        OffsetDateTime end
) {
}
