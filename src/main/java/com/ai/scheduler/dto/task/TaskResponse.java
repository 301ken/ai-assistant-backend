package com.ai.scheduler.dto.task;

import com.ai.scheduler.entity.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record TaskResponse(
        Long id,
        Long userId,
        String title,
        Integer priorityWeight,
        TaskStatus status,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        boolean repeatWeekly,
        LocalDateTime createdAt
) {
}
