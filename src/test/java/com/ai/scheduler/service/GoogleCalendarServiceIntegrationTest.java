package com.ai.scheduler.service;

import com.ai.scheduler.dto.calendar.CalendarColor;
import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventRequest;
import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration test against the real Google Calendar API.
 *
 * Set the environment variable GOOGLE_ACCESS_TOKEN before running:
 *
 *   export GOOGLE_ACCESS_TOKEN="ya29...."
 *   ./mvnw test -Dtest=GoogleCalendarServiceIntegrationTest -pl .
 *
 * The test is automatically skipped when the variable is not set,
 * so it never fails in CI without credentials.
 */
class GoogleCalendarServiceIntegrationTest {

    private static final String TOKEN_ENV = "GOOGLE_ACCESS_TOKEN";
    private static final String TIME_ZONE = "Africa/Algiers"; // UTC+1, adjust as needed

    private GoogleCalendarService service;
    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = System.getenv(TOKEN_ENV);
        Assumptions.assumeTrue(
                accessToken != null && !accessToken.isBlank(),
                "Skipping: " + TOKEN_ENV + " environment variable is not set");
        service = new GoogleCalendarService();
    }

    @Test
    void createEvent_shouldReturnCreatedEventWithCorrectFields() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of(TIME_ZONE));
        CalendarEventRequest request = new CalendarEventRequest(
                "AI Scheduler – Integration Test",
                "Automatically created by GoogleCalendarServiceIntegrationTest",
                CalendarColor.PEACOCK,
                now.plusHours(1),
                now.plusHours(2),
                TIME_ZONE,
                null
        );

        CalendarEventResponse response = service.createEvent(accessToken, request);

        assertThat(response.id()).isNotBlank();
        assertThat(response.title()).isEqualTo("AI Scheduler – Integration Test");
        assertThat(response.colorId()).isEqualTo(CalendarColor.PEACOCK.getColorId());
        assertThat(response.htmlLink()).isNotBlank();
        assertThat(response.recurrence()).isNull();

        System.out.println("Created event: " + response.htmlLink());
    }

    @Test
    void getEventsForDay_shouldReturnListForToday() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of(TIME_ZONE));

        List<CalendarEventResponse> events = service.getEventsForDay(accessToken, today, TIME_ZONE);

        assertThat(events).isNotNull();
        System.out.println("Events today (" + today + "): " + events.size());
        events.forEach(e -> System.out.printf("  [%s] %s  %s -> %s  recurrence=%s%n",
                e.colorId(), e.title(), e.startDateTime(), e.endDateTime(), e.recurrence()));
    }

    @Test
    void createRecurringEvent_shouldReturnEventWithRecurrenceRule() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of(TIME_ZONE));
        List<String> rrule = List.of("RRULE:FREQ=WEEKLY;COUNT=4");

        CalendarEventRequest request = new CalendarEventRequest(
                "AI Scheduler – Recurring Test",
                "Weekly recurring event created by integration test",
                CalendarColor.SAGE,
                now.plusHours(3),
                now.plusHours(4),
                TIME_ZONE,
                rrule
        );

        CalendarEventResponse response = service.createEvent(accessToken, request);

        assertThat(response.id()).isNotBlank();
        assertThat(response.title()).isEqualTo("AI Scheduler – Recurring Test");
        assertThat(response.recurrence()).isNotNull();
        assertThat(response.recurrence()).anyMatch(r -> r.contains("RRULE"));

        System.out.println("Created recurring event: " + response.htmlLink());
        System.out.println("Recurrence: " + response.recurrence());
    }

    @Test
    void getEventsForDays_shouldReturnEventsAcrossMultipleDays() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of(TIME_ZONE));
        List<LocalDate> days = List.of(today.minusDays(1), today, today.plusDays(1));

        List<CalendarEventResponse> events = service.getEventsForDays(accessToken, days, TIME_ZONE);

        assertThat(events).isNotNull();
        System.out.println("Events over 3 days: " + events.size());
        events.forEach(e -> System.out.printf("  [%s] %s  %s -> %s  recurrence=%s%n",
                e.colorId(), e.title(), e.startDateTime(), e.endDateTime(), e.recurrence()));
    }
}
