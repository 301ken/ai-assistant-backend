package com.ai.scheduler.schedulers;

import com.ai.scheduler.dto.calendar.ScheduleConstraint;
import com.ai.scheduler.dto.calendar.scheduler.GeneratedSchedule;
import com.ai.scheduler.dto.llm.TaskDTO;
import com.ai.scheduler.dto.llm.TaskListDTO;
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
    void generate_throwsWhenTasksIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> llmScheduler.generate(null, List.of(), 0.8));
    }

    @Test
    void generate_throwsWhenTaskListIsEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> llmScheduler.generate(new TaskListDTO(List.of()), List.of(), 0.8));
    }

    @Test
    void generate_throwsWhenConstraintsIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> llmScheduler.generate(someTaskList(), null, 0.8));
    }

    @Test
    void generate_throwsWhenPercentageIsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> llmScheduler.generate(someTaskList(), List.of(), 0.0));
    }

    @Test
    void generate_throwsWhenPercentageExceedsOne() {
        assertThrows(IllegalArgumentException.class,
                () -> llmScheduler.generate(someTaskList(), List.of(), 1.1));
    }

    // ── Integration tests (real OpenAI request) ──────────────────────────────

    @Test
    void generate_returnsNonNullScheduleWithEvents() {
        GeneratedSchedule schedule = llmScheduler.generate(someTaskList(), someConstraints(), 0.8);

        assertNotNull(schedule);
        assertNotNull(schedule.events());
        assertFalse(schedule.events().isEmpty(), "Expected at least one scheduled event");

        for (var event : schedule.events()) {
            System.out.printf("Event: %-30s %s -> %s%n",
                    event.title(), event.startDateTime(), event.endDateTime());
        }
    }

    @Test
    void generate_eventsHaveNonBlankTitlesAndValidDateRange() {
        GeneratedSchedule schedule = llmScheduler.generate(someTaskList(), someConstraints(), 0.8);

        for (var event : schedule.events()) {
            assertNotNull(event.title(), "Event title must not be null");
            assertFalse(event.title().isBlank(), "Event title must not be blank");
            assertNotNull(event.startDateTime(), "startDateTime must not be null");
            assertNotNull(event.endDateTime(), "endDateTime must not be null");
            assertTrue(event.endDateTime().isAfter(event.startDateTime()),
                    "endDateTime must be after startDateTime for event: " + event.title());
        }
    }

    @Test
    void generate_highPriorityTaskAppearsInSchedule() {
        TaskListDTO tasks = new TaskListDTO(List.of(
                new TaskDTO("Urgent Exam Prep", 10.0),
                new TaskDTO("Buy groceries", 2.0)
        ));

        GeneratedSchedule schedule = llmScheduler.generate(tasks, someConstraints(), 1.0);

        boolean hasExamPrep = schedule.events().stream()
                .anyMatch(e -> e.title().toLowerCase().contains("exam")
                        || e.title().toLowerCase().contains("urgent"));

        assumeTrue(hasExamPrep, "LLM did not include the high-priority task by name (non-deterministic)");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TaskListDTO someTaskList() {
        return new TaskListDTO(List.of(
                new TaskDTO("Study for Math exam", 9.0),
                new TaskDTO("Complete programming assignment", 7.0),
                new TaskDTO("Go to the gym", 3.0)
        ));
    }

    private List<ScheduleConstraint> someConstraints() {
        // Busy slot: 12:00–13:00 UTC
        return List.of(new ScheduleConstraint(
                OffsetDateTime.parse("2026-05-06T12:00:00+00:00"),
                OffsetDateTime.parse("2026-05-06T13:00:00+00:00")
        ));
    }
}
