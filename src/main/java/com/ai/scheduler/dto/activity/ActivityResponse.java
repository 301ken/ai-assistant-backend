package com.ai.scheduler.dto.activity;

import com.ai.scheduler.entity.ActivityType;
import java.time.LocalDate;
import java.time.LocalTime;

public record ActivityResponse(
        Long id,
        Long userId,
        Long taskId,
        ActivityType activityType,
        String activityDescription,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
}
