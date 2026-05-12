package com.ai.scheduler.dto;

import com.ai.scheduler.dto.time.TimeRange;

public record ScheduleGenerationRequest(
        String prompt,
        TimeRange timeRange,
        double percentage,
        boolean recurrent,
        /** Selects the scheduling engine: "llm" (default) or "proportional". */
        String schedulerType
) {
}
