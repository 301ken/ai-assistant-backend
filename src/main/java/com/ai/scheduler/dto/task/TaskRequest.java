package com.ai.scheduler.dto.task;

import com.ai.scheduler.entity.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record TaskRequest(
        @NotBlank String title,
        @NotNull @Min(1) @Max(10) Integer priorityWeight,
        TaskStatus status,
        @NotNull LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean repeatWeekly
) {
}
