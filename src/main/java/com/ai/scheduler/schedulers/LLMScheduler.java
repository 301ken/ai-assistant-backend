package com.ai.scheduler.schedulers;

import com.ai.scheduler.dto.calendar.ScheduleConstraint;
import com.ai.scheduler.dto.calendar.scheduler.GeneratedSchedule;
import com.ai.scheduler.dto.calendar.scheduler.SchedulingRequest;
import com.ai.scheduler.dto.time.TimeRange;
import com.ai.scheduler.service.llm_generic.DefaultLlmStructuredClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class LLMScheduler implements Scheduler {

    private final DefaultLlmStructuredClient structuredClient;
    private final ObjectMapper objectMapper;

    public LLMScheduler(DefaultLlmStructuredClient structuredClient,
                        ObjectMapper objectMapper) {
        this.structuredClient = structuredClient;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

    @Override
    public GeneratedSchedule generate(SchedulingRequest request) {

        validateInput(request);

        String prompt = buildPrompt(request);

        return structuredClient.generateStructuredResponse(
                prompt,
                GeneratedSchedule.class
        );
    }

    // ----------------------------
    // Input validation layer
    // ----------------------------
    private void validateInput(SchedulingRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        if (request.tasks() == null ||
                request.tasks().tasks() == null ||
                request.tasks().tasks().isEmpty()) {
            throw new IllegalArgumentException("Task list cannot be empty");
        }

        if (request.constraints() == null) {
            throw new IllegalArgumentException("Constraints cannot be null");
        }

        if (request.percentageOfTimeToUse() <= 0 || request.percentageOfTimeToUse() > 1) {
            throw new IllegalArgumentException("percentageOfTimeToUse must be between 0 and 1");
        }

        if (request.timeRange() == null ||
                request.timeRange().from() == null ||
                request.timeRange().to() == null) {
            throw new IllegalArgumentException("Time range must be defined");
        }
    }

    // ----------------------------
    // Prompt builder
    // ----------------------------
    private String buildPrompt(SchedulingRequest request) {

        String tasksJson = toJson(request.tasks().tasks());
        String constraintsJson = toJson(request.constraints());

        double percentage = request.percentageOfTimeToUse();
        TimeRange range = request.timeRange();
        boolean recurrent = request.recurrent();

        return """
                You are a scheduling engine.

                SCHEDULING CONTEXT:
                - Time Range: %s to %s
                - Recurring: %s
                - Time Usage Target: %f (0–1 scale of available time)

                TASK RULES:
                - Each task has a title and priority (1–10)
                - Higher priority tasks should be scheduled earlier and allocated more time

                CONSTRAINT RULES:
                - All constraints are HARD constraints and must be strictly respected
                - Do not create overlapping events
                - Stay fully within the given time range

                TIME USAGE ENFORCEMENT:
                - The total schedulable time is the duration between Time Range start and end, minus all constraint periods
                - Estimate total available time in hours

                - Compute:
                  targetScheduledTime = availableTime × %f

                - Your scheduled events MUST approximately match this target

                - Acceptable tolerance: ±10%%
                - If too little time is scheduled → add more sessions or extend tasks
                - If too much → reduce or remove low priority tasks

                - DO NOT leave large portions of time unused
                - Distribute tasks across the full time range
                - Split large tasks into multiple sessions when needed

                BALANCING RULES:
                - Avoid consecutive high-focus tasks
                - Insert breaks when needed

                COLOR RULES:
                - High priority → TOMATO / FLAMINGO / GRAPE
                - Medium priority → PEACOCK / BLUEBERRY / SAGE
                - Low priority or breaks → BASIL / LAVENDER / GRAPHITE

                RECURRENCE RULE:
                - If Recurring = false:
                  → Do NOT include any recurrence field
                - If Recurring = true:
                  → Each event MUST include:
                        "recurrence": ["RRULE:..."]
                  → Use valid Google Calendar RRULE format (RFC 5545)
                  → Example:
                        "recurrence": ["RRULE:FREQ=WEEKLY;BYDAY=MO"]
                  → Generate only ONE instance per event (no duplication)
                REALISM CONSTRAINTS (STRICT):
                
                - No single event may exceed 3 hours duration
                - Minimum event duration: 30 minutes
                
                - After each 1.5–2 hours of work:
                  → you MUST insert a break (15–30 minutes)
                
                - A single day should not exceed 8–10 hours of scheduled time
                
                - You MUST distribute tasks across multiple days within the time range
                  → do NOT concentrate all work into 1–2 days
                
                - Sleeping hours (approx 22:00–07:00) must NOT contain events
                
                - Schedules must resemble realistic human daily routines
                
                ANTI-CHEATING RULE:
                
                - Do NOT satisfy time usage by creating extremely long events
                - Do NOT compress all work into a small number of days
                - You MUST balance time across the full time range

                OUTPUT FORMAT (STRICT JSON ONLY):
                {
                  "events": [
                    {
                      "title": "string",
                      "description": "string",
                      "startDateTime": "ISO-8601 datetime with offset",
                      "endDateTime": "ISO-8601 datetime with offset",
                      "color": "1-11",
                      "recurrence": ["RRULE:..."] // only if Recurring = true
                    }
                  ]
                }

                INPUT TASKS:
                %s

                INPUT CONSTRAINTS:
                %s
                """.formatted(
                range.from(),
                range.to(),
                recurrent,
                percentage,
                percentage,
                tasksJson,
                constraintsJson
        );
    }

    // ----------------------------
    // JSON helper
    // ----------------------------
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }
}