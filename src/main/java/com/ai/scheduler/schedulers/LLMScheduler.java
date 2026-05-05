package com.ai.scheduler.schedulers;

import com.ai.scheduler.dto.calendar.ScheduleConstraint;
import com.ai.scheduler.dto.calendar.scheduler.GeneratedSchedule;
import com.ai.scheduler.dto.llm.TaskListDTO;
import com.ai.scheduler.service.llm_generic.DefaultLlmStructuredClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;

public class LLMScheduler implements Scheduler {

    private final DefaultLlmStructuredClient structuredClient;
    private final ObjectMapper objectMapper;

    public LLMScheduler(DefaultLlmStructuredClient structuredClient,
                        ObjectMapper objectMapper) {
        this.structuredClient = structuredClient;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

    @Override
    public GeneratedSchedule generate(TaskListDTO tasks,
                                      List<ScheduleConstraint> constraints,
                                      double percentageOfTimeToUse) {

        validateInput(tasks, constraints, percentageOfTimeToUse);

        String prompt = buildPrompt(tasks, constraints, percentageOfTimeToUse);

        return structuredClient.generateStructuredResponse(
                prompt,
                GeneratedSchedule.class
        );
    }

    // ----------------------------
    // Input validation layer
    // ----------------------------
    private void validateInput(TaskListDTO tasks,
                               List<ScheduleConstraint> constraints,
                               double percentage) {

        if (tasks == null || tasks.tasks() == null || tasks.tasks().isEmpty()) {
            throw new IllegalArgumentException("Task list cannot be empty");
        }

        if (constraints == null) {
            throw new IllegalArgumentException("Constraints cannot be null");
        }

        if (percentage <= 0 || percentage > 1) {
            throw new IllegalArgumentException("percentageOfTimeToUse must be between 0 and 1");
        }
    }

    // ----------------------------
    // Prompt builder (isolated)
    // ----------------------------
    private String buildPrompt(TaskListDTO tasks,
                               List<ScheduleConstraint> constraints,
                               double percentage) {

        String tasksJson = toJson(tasks.tasks());
        String constraintsJson = toJson(constraints);

        return """
                You are a scheduling engine.

                You receive:
                - tasks
                - scheduling constraints
                - percentage of available time to use

                TASK RULES:
                - Each task has a title and priority (1–10)
                - Higher priority tasks should be scheduled earlier and allocated more time

                CONSTRAINT RULES:
                - All constraints are HARD constraints and must be strictly respected
                - No overlapping events allowed

                TIME RULE:
                - Use only %f of available time (0–1 scale)

                BALANCING RULES:
                - Avoid consecutive high-focus tasks
                - Insert breaks when needed

                COLOR RULES:
                - TOMATO / FLAMINGO / GRAPE → high priority
                - PEACOCK / BLUEBERRY / SAGE → medium priority
                - BASIL / LAVENDER / GRAPHITE → low priority or breaks

                OUTPUT FORMAT (STRICT JSON ONLY):
                {
                  "events": [
                    {
                      "title": "string",
                      "description": "string",
                      "startDateTime": "ISO-8601 datetime with offset (e.g. 2026-05-06T09:00:00+00:00)",
                      "endDateTime": "ISO-8601 datetime with offset (e.g. 2026-05-06T10:00:00+00:00)",
                      "color": "1-11"
                    }
                  ]
                }

                INPUT TASKS:
                %s

                INPUT CONSTRAINTS:
                %s
                """.formatted(percentage, tasksJson, constraintsJson);
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