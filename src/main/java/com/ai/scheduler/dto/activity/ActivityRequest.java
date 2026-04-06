package com.ai.scheduler.dto.activity;

import com.ai.scheduler.entity.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record ActivityRequest(
        Long taskId,
        @NotNull ActivityType activityType,
        @NotBlank String activityDescription,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {
}
