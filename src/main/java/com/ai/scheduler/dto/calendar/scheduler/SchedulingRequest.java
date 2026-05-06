package com.ai.scheduler.dto.calendar.scheduler;

import com.ai.scheduler.dto.calendar.ScheduleConstraint;
import com.ai.scheduler.dto.llm.TaskDTO;
import com.ai.scheduler.dto.llm.TaskListDTO;
import com.ai.scheduler.dto.time.TimeRange;

import java.util.List;

public record SchedulingRequest(
        TaskListDTO tasks,
        List<ScheduleConstraint> constraints,
        TimeRange timeRange,
        double percentageOfTimeToUse,
        boolean recurrent
)
{}
