package com.ai.scheduler.controller;

import com.ai.scheduler.dto.ScheduleGenerationRequest;
import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventResponse;
import com.ai.scheduler.service.SchedulingWorkflowService;
import com.ai.scheduler.util.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engine")
public class SchedulingWorkflowController {

    private final SchedulingWorkflowService schedulingWorkflowService;

    public SchedulingWorkflowController(SchedulingWorkflowService schedulingWorkflowService) {
        this.schedulingWorkflowService = schedulingWorkflowService;
    }

    @PostMapping("/generate")
    public ResponseEntity<List<CalendarEventResponse>> generateSchedule(
            @Valid @RequestBody ScheduleGenerationRequest request) {
        Long userId = SecurityUtils.currentUserId();
        List<CalendarEventResponse> response = schedulingWorkflowService.generateSchedule(
                userId,
                request.prompt(),
                request.timeRange(),
                request.percentage(),
                request.recurrent());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

