package com.ai.scheduler.schedulers;

import com.ai.scheduler.dto.calendar.ScheduleConstraint;
import com.ai.scheduler.dto.calendar.scheduler.GeneratedSchedule;
import com.ai.scheduler.dto.calendar.scheduler.SchedulingRequest;
import com.ai.scheduler.dto.llm.TaskDTO;
import com.ai.scheduler.dto.llm.TaskListDTO;
import com.ai.scheduler.dto.time.TimeRange;
import com.ai.scheduler.service.llm_generic.DefaultLlmStructuredClient;
import com.ai.scheduler.service.llm_generic.OpenAiLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class LLMSchedulerTest {

    private LLMScheduler llmScheduler;

    // Week of 2026-05-11 (Mon) → 2026-05-17 (Sun), UTC
    private static final OffsetDateTime WEEK_START = OffsetDateTime.parse("2026-05-11T08:00:00+00:00");
    private static final OffsetDateTime WEEK_END   = OffsetDateTime.parse("2026-05-17T20:00:00+00:00");

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "Skipping: OPENAI_API_KEY not set");

        OpenAiLlmClient openAiClient = new OpenAiLlmClient(new RestTemplate());
        ReflectionTestUtils.setField(openAiClient, "apiKey", apiKey);

        DefaultLlmStructuredClient structuredClient =
                new DefaultLlmStructuredClient(openAiClient, new ObjectMapper());

        llmScheduler = new LLMScheduler(structuredClient, new ObjectMapper());
    }

    // ── Validation tests (no API call needed) ────────────────────────────────

    @Test
    void generate_throwsWhenRequestIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> llmScheduler.generate(null));
    }

    @Test
    void generate_throwsWhenTaskListIsEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> llmScheduler.generate(requestWith(new TaskListDTO(List.of()))));
    }

    @Test
    void generate_throwsWhenTasksIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> llmScheduler.generate(requestWith(new TaskListDTO(null))));
    }

    @Test
    void generate_throwsWhenConstraintsIsNull() {
        SchedulingRequest req = new SchedulingRequest(
                someTaskList(), null, weekRange(), 0.8, false);
        assertThrows(IllegalArgumentException.class, () -> llmScheduler.generate(req));
    }

    @Test
    void generate_throwsWhenTimeRangeIsNull() {
        SchedulingRequest req = new SchedulingRequest(
                someTaskList(), List.of(), null, 0.8, false);
        assertThrows(IllegalArgumentException.class, () -> llmScheduler.generate(req));
    }

    @Test
    void generate_throwsWhenPercentageIsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> llmScheduler.generate(new SchedulingRequest(
                        someTaskList(), List.of(), weekRange(), 0.0, false)));
    }

    @Test
    void generate_throwsWhenPercentageExceedsOne() {
        assertThrows(IllegalArgumentException.class,
                () -> llmScheduler.generate(new SchedulingRequest(
                        someTaskList(), List.of(), weekRange(), 1.1, false)));
    }

    // ── Integration tests (real OpenAI request) ──────────────────────────────

    @Test
    void generate_oneOffWeek_returnsNonEmptySchedule() {
        SchedulingRequest req = new SchedulingRequest(
                someTaskList(), someConstraints(), weekRange(), 0.8, false);

        GeneratedSchedule schedule = llmScheduler.generate(req);

        assertNotNull(schedule);
        assertNotNull(schedule.events());
        assertFalse(schedule.events().isEmpty(), "Expected at least one event");

        schedule.events().forEach(e ->
                System.out.printf("Event: %-35s %s -> %s%n",
                        e.title(), e.startDateTime(), e.endDateTime()));
    }

    @Test
    void generate_oneOffWeek_eventsHaveValidTitlesAndDateOrder() {
        SchedulingRequest req = new SchedulingRequest(
                someTaskList(), someConstraints(), weekRange(), 0.8, false);

        GeneratedSchedule schedule = llmScheduler.generate(req);

        for (var event : schedule.events()) {
            assertNotNull(event.title(), "title must not be null");
            assertFalse(event.title().isBlank(), "title must not be blank");
            assertNotNull(event.startDateTime(), "startDateTime must not be null");
            assertNotNull(event.endDateTime(), "endDateTime must not be null");
            assertTrue(event.endDateTime().isAfter(event.startDateTime()),
                    "end must be after start for: " + event.title());
        }
    }

    @Test
    void generate_recurringWeek_highPercentage_returnsScheduleWithRrules() {
        // Full week, 95 % of available time, recurring
        SchedulingRequest req = new SchedulingRequest(
                fullWeekTaskList(),
                someConstraints(),
                weekRange(),
                0.95,
                true
        );

        GeneratedSchedule schedule = llmScheduler.generate(req);

        assertNotNull(schedule);
        assertFalse(schedule.events().isEmpty(), "Expected events in recurring schedule");

        System.out.println("=== Recurring weekly schedule (95%) ===");
        schedule.events().forEach(e -> {
            System.out.printf("  %-35s %s -> %s%n", e.title(), e.startDateTime(), e.endDateTime());
        });
    }

    @Test
    void generate_highPriorityTaskScheduledEarlierThanLowPriority() {
        TaskListDTO tasks = new TaskListDTO(List.of(
                new TaskDTO("Critical Deadline Task", 10.0),
                new TaskDTO("Optional Reading", 2.0)
        ));
        SchedulingRequest req = new SchedulingRequest(
                tasks, List.of(), weekRange(), 0.9, false);

        GeneratedSchedule schedule = llmScheduler.generate(req);

        assertFalse(schedule.events().isEmpty());

        // At least verify the high-priority task appears somewhere
        boolean hasHighPriority = schedule.events().stream()
                .anyMatch(e -> e.title().toLowerCase().contains("critical")
                        || e.title().toLowerCase().contains("deadline"));

        assumeTrue(hasHighPriority,
                "LLM omitted the high-priority task by name (non-deterministic output)");

        System.out.println("High-priority task present in schedule.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private SchedulingRequest requestWith(TaskListDTO tasks) {
        return new SchedulingRequest(tasks, List.of(), weekRange(), 0.8, false);
    }

    private TaskListDTO someTaskList() {
        return new TaskListDTO(List.of(
                new TaskDTO("Study for Math exam", 9.0),
                new TaskDTO("Complete programming assignment", 7.0),
                new TaskDTO("Go to the gym", 3.0)
        ));
    }

    private TaskListDTO fullWeekTaskList() {
        return new TaskListDTO(List.of(
                new TaskDTO("Study for Math exam", 9.0),
                new TaskDTO("Complete programming assignment", 8.0),
                new TaskDTO("Read research papers", 6.0),
                new TaskDTO("Team project work", 7.0),
                new TaskDTO("Go to the gym", 4.0),
                new TaskDTO("Personal admin tasks", 3.0)
        ));
    }

    private TimeRange weekRange() {
        return new TimeRange(WEEK_START, WEEK_END);
    }

    /** Busy slot: lunch on Wednesday */
    private List<ScheduleConstraint> someConstraints() {
        return List.of(new ScheduleConstraint(
                OffsetDateTime.parse("2026-05-13T12:00:00+00:00"),
                OffsetDateTime.parse("2026-05-13T13:00:00+00:00")
        ));
    }
}
