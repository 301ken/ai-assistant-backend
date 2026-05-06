package com.ai.scheduler.service;

import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventResponse;
import com.ai.scheduler.dto.time.TimeRange;
import com.ai.scheduler.schedulers.LLMScheduler;
import com.ai.scheduler.service.llm_generic.DefaultLlmStructuredClient;
import com.ai.scheduler.service.llm_generic.OpenAiLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Full end-to-end integration test for SchedulingWorkflowService.
 *
 * Reads credentials from the .env file in the project root.
 * Required .env keys: OPENAI_API_KEY, ACCESS_TOKEN
 *
 * Run with:
 *   ./mvnw test -Dtest=SchedulingWorkflowServiceIntegrationTest
 */
class SchedulingWorkflowServiceIntegrationTest {

    // May 4 (Mon) → May 10 (Sun) 2026, UTC
    private static final OffsetDateTime RANGE_START = OffsetDateTime.parse("2026-05-04T08:00:00+00:00");
    private static final OffsetDateTime RANGE_END   = OffsetDateTime.parse("2026-05-10T20:00:00+00:00");

    private static final String RICH_PROMPT = """
            I have a really busy week ahead. Here are all the things I need to get done:

            1. Study for my Advanced Algorithms final exam – critical, needs at least 8 hours total.
            2. Finish the machine learning project report – due Friday, high priority, about 5 hours.
            3. Complete the database assignment with SQL queries and normalization – around 3 hours.
            4. Prepare a 10-minute presentation on distributed systems for class on Thursday.
            5. Review and respond to all pending emails and messages – low priority, 1 hour.
            6. Go to the gym at least 3 times this week, each session about 1.5 hours.
            7. Read two chapters of the software engineering textbook for the quiz on Friday.
            8. Work on the open-source contribution I promised – fix two bugs, around 2 hours.
            9. Cook meals and do grocery shopping – life admin, roughly 2 hours spread across the week.
            10. Call my parents – personal, 30 minutes.
            11. Complete the online course module on Spring Boot security – 2 hours.
            12. Draft the project proposal for the upcoming team meeting next Monday.
            """;

    private SchedulingWorkflowService workflowService;

    private static Map<String, String> loadDotEnv() {
        Map<String, String> env = new HashMap<>(System.getenv());
        for (Path candidate : List.of(Path.of(".env"), Path.of("../../.env"))) {
            if (Files.exists(candidate)) {
                try {
                    for (String line : Files.readAllLines(candidate)) {
                        line = line.strip();
                        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                        int idx = line.indexOf('=');
                        String key = line.substring(0, idx).strip();
                        String value = line.substring(idx + 1).strip()
                                .replaceAll("^\"|\"$", "")
                                .replaceAll("^'|'$", "");
                        env.put(key, value);
                    }
                } catch (IOException ignored) {}
                break;
            }
        }
        return env;
    }

    @BeforeEach
    void setUp() {
        Map<String, String> env = loadDotEnv();

        String openAiApiKey   = env.get("OPENAI_API_KEY");
        String accessToken    = env.get("ACCESS_TOKEN");

        assumeTrue(openAiApiKey != null && !openAiApiKey.isBlank(),
                "Skipping: OPENAI_API_KEY not set in .env");
        assumeTrue(accessToken != null && !accessToken.isBlank(),
                "Skipping: ACCESS_TOKEN not set in .env");

        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper objectMapper = new ObjectMapper();

        OpenAiLlmClient openAiClient = new OpenAiLlmClient(restTemplate);
        ReflectionTestUtils.setField(openAiClient, "apiKey", openAiApiKey);

        DefaultLlmStructuredClient structuredClient = new DefaultLlmStructuredClient(openAiClient, objectMapper);
        TaskExtractionService taskExtractionService = new TaskExtractionService(structuredClient);
        LLMScheduler scheduler = new LLMScheduler(structuredClient, objectMapper);

        // Returns the ACCESS_TOKEN from .env directly — no DB needed
        GoogleOAuthTokenService tokenService = new GoogleOAuthTokenService(
                null, null, restTemplate, objectMapper) {
            @Override
            public String getValidAccessToken(Long userId) {
                return accessToken;
            }
        };

        workflowService = new SchedulingWorkflowService(
                new GoogleCalendarService(),
                tokenService,
                taskExtractionService,
                scheduler
        );
    }

    @Test
    void generateSchedule_createsEventsInGoogleCalendar() {
        List<CalendarEventResponse> created = workflowService.generateSchedule(
                1L, RICH_PROMPT, new TimeRange(RANGE_START, RANGE_END), 0.8, false);

        assertNotNull(created);
        assertFalse(created.isEmpty(), "Expected at least one event created in Google Calendar");

        System.out.println("=== Created " + created.size() + " events in Google Calendar ===");
        created.forEach(e -> System.out.printf(
                "  [%s] %-40s  %s -> %s%n  link: %s%n",
                e.colorId(), e.title(), e.startDateTime(), e.endDateTime(), e.htmlLink()));
    }

    @Test
    void generateSchedule_allCreatedEventsHaveValidFields() {
        List<CalendarEventResponse> created = workflowService.generateSchedule(
                1L, RICH_PROMPT, new TimeRange(RANGE_START, RANGE_END), 0.8, false);

        for (CalendarEventResponse e : created) {
            assertNotNull(e.id(),            "id must not be null for: " + e.title());
            assertNotNull(e.title(),         "title must not be null");
            assertFalse(e.title().isBlank(), "title must not be blank");
            assertNotNull(e.startDateTime(), "startDateTime must not be null: " + e.title());
            assertNotNull(e.endDateTime(),   "endDateTime must not be null: " + e.title());
            assertTrue(e.endDateTime().isAfter(e.startDateTime()),
                    "end must be after start for: " + e.title());
            assertNotNull(e.htmlLink(),      "htmlLink must not be null: " + e.title());
        }
    }

    @Test
    void generateSchedule_recurringMode_createsEvents() {
        List<CalendarEventResponse> created = workflowService.generateSchedule(
                1L, RICH_PROMPT, new TimeRange(RANGE_START, RANGE_END), 0.9, true);

        assertFalse(created.isEmpty(), "Expected events in recurring schedule");

        System.out.println("=== Recurring schedule: " + created.size() + " events ===");
        created.forEach(e -> System.out.printf(
                "  %-40s  %s -> %s  recurrence=%s%n",
                e.title(), e.startDateTime(), e.endDateTime(), e.recurrence()));
    }
}
