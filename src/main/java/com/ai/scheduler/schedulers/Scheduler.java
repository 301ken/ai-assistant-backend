package com.ai.scheduler.schedulers;

import com.ai.scheduler.dto.calendar.ScheduleConstraint;
import com.ai.scheduler.dto.calendar.scheduler.GeneratedSchedule;
import com.ai.scheduler.dto.calendar.scheduler.SchedulingRequest;
import com.ai.scheduler.dto.llm.TaskListDTO;

import java.util.List;

public interface Scheduler {
    GeneratedSchedule generate(SchedulingRequest request);
}
