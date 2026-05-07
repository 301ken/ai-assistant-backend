package com.ai.scheduler.service;

import com.ai.scheduler.dto.activity.ActivityStatsResponse;
import com.ai.scheduler.dto.activity.EventComparisonResponse;
import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventResponse;
import com.ai.scheduler.entity.Activity;
import com.ai.scheduler.entity.ActivityType;
import com.ai.scheduler.entity.User;
import com.ai.scheduler.repository.ActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityStatisticsServiceTest {

    private static final Long USER_ID = 42L;
    private static final String ACCESS_TOKEN = "test-token";

    @Mock private ActivityRepository activityRepository;
    @Mock private GoogleCalendarService googleCalendarService;
    @Mock private GoogleOAuthTokenService googleOAuthTokenService;

    private ActivityStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new ActivityStatisticsService(activityRepository, googleCalendarService, googleOAuthTokenService);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Activity activity(LocalDate date, LocalTime start, LocalTime end) {
        return activity(date, start, end, null, null);
    }

    private Activity activity(LocalDate date, LocalTime start, LocalTime end,
                               String calendarEventId, String calendarEventTitle) {
        User user = new User();
        Activity a = new Activity();
        a.setUser(user);
        a.setActivityType(ActivityType.FOCUS);
        a.setDescription("desc");
        a.setDate(date);
        a.setStartTime(start);
        a.setEndTime(end);
        a.setCalendarEventId(calendarEventId);
        a.setCalendarEventTitle(calendarEventTitle);
        return a;
    }

    private CalendarEventResponse calEvent(String title, OffsetDateTime start, OffsetDateTime end) {
        return new CalendarEventResponse("id-" + title, title, null, null, start, end, "UTC", null, null);
    }

    // -------------------------------------------------------------------------
    // calculateStatistics
    // -------------------------------------------------------------------------

    @Test
    void calculateStatistics_noActivities_returnsZeroes() {
        LocalDate from = LocalDate.of(2026, 5, 4);
        LocalDate to   = LocalDate.of(2026, 5, 10);
        when(activityRepository.findByUserIdAndDateBetween(USER_ID, from, to)).thenReturn(List.of());

        ActivityStatsResponse result = service.calculateStatistics(USER_ID, from, to);

        assertThat(result.getTotalSeconds()).isZero();
        assertThat(result.getTotalHours()).isZero();
        assertThat(result.getSessionCount()).isZero();
        assertThat(result.getDailyBreakdown()).isEmpty();
    }

    @Test
    void calculateStatistics_singleActivity_returnsCorrectStats() {
        LocalDate date = LocalDate.of(2026, 5, 5);
        // 09:00 – 10:30 = 5400 seconds
        Activity a = activity(date, LocalTime.of(9, 0), LocalTime.of(10, 30));
        when(activityRepository.findByUserIdAndDateBetween(USER_ID, date, date)).thenReturn(List.of(a));

        ActivityStatsResponse result = service.calculateStatistics(USER_ID, date, date);

        assertThat(result.getTotalSeconds()).isEqualTo(5400L);
        assertThat(result.getTotalHours()).isEqualTo(5400.0 / 3600.0);
        assertThat(result.getSessionCount()).isEqualTo(1);
        assertThat(result.getDailyBreakdown()).hasSize(1);
        assertThat(result.getDailyBreakdown().get(0).getDate()).isEqualTo(date);
        assertThat(result.getDailyBreakdown().get(0).getSeconds()).isEqualTo(5400L);
    }

    @Test
    void calculateStatistics_multipleActivitiesSameDay_aggregatesIntoDailyEntry() {
        LocalDate date = LocalDate.of(2026, 5, 6);
        // 3600 + 1800 = 5400 seconds on same day
        Activity a1 = activity(date, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Activity a2 = activity(date, LocalTime.of(10, 0), LocalTime.of(10, 30));
        when(activityRepository.findByUserIdAndDateBetween(USER_ID, date, date)).thenReturn(List.of(a1, a2));

        ActivityStatsResponse result = service.calculateStatistics(USER_ID, date, date);

        assertThat(result.getSessionCount()).isEqualTo(2);
        assertThat(result.getTotalSeconds()).isEqualTo(5400L);
        assertThat(result.getDailyBreakdown()).hasSize(1);
        assertThat(result.getDailyBreakdown().get(0).getSeconds()).isEqualTo(5400L);
    }

    @Test
    void calculateStatistics_activitiesOnDifferentDays_producesMultipleDailyEntries() {
        LocalDate day1 = LocalDate.of(2026, 5, 4);
        LocalDate day2 = LocalDate.of(2026, 5, 5);
        Activity a1 = activity(day1, LocalTime.of(9, 0),  LocalTime.of(10, 0)); // 3600s
        Activity a2 = activity(day2, LocalTime.of(14, 0), LocalTime.of(15, 0)); // 3600s
        when(activityRepository.findByUserIdAndDateBetween(USER_ID, day1, day2)).thenReturn(List.of(a1, a2));

        ActivityStatsResponse result = service.calculateStatistics(USER_ID, day1, day2);

        assertThat(result.getTotalSeconds()).isEqualTo(7200L);
        assertThat(result.getSessionCount()).isEqualTo(2);
        assertThat(result.getDailyBreakdown()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // getAssignableEvents
    // -------------------------------------------------------------------------

    @Test
    void getAssignableEvents_callsCalendarWithCorrectTimeWindow() throws Exception {
        LocalDate date = LocalDate.of(2026, 5, 7);
        List<CalendarEventResponse> expected = List.of(
                calEvent("Meeting", date.atStartOfDay().atOffset(ZoneOffset.UTC),
                         date.atStartOfDay().atOffset(ZoneOffset.UTC).plusHours(1)));
        when(googleOAuthTokenService.getValidAccessToken(USER_ID)).thenReturn(ACCESS_TOKEN);
        when(googleCalendarService.getEventsInTimeRange(any(), any(), any())).thenReturn(expected);

        List<CalendarEventResponse> result = service.getAssignableEvents(USER_ID, date);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toCaptor   = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(googleCalendarService).getEventsInTimeRange(eq(ACCESS_TOKEN), fromCaptor.capture(), toCaptor.capture());

        // from = date - 1 day at midnight UTC, to = date + 2 days at midnight UTC
        OffsetDateTime expectedFrom = date.minusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime expectedTo   = date.plusDays(2).atStartOfDay().atOffset(ZoneOffset.UTC);
        assertThat(fromCaptor.getValue()).isEqualTo(expectedFrom);
        assertThat(toCaptor.getValue()).isEqualTo(expectedTo);
    }

    @Test
    void getAssignableEvents_wrapsIoExceptionAsRuntimeException() throws Exception {
        LocalDate date = LocalDate.of(2026, 5, 7);
        when(googleOAuthTokenService.getValidAccessToken(USER_ID)).thenReturn(ACCESS_TOKEN);
        when(googleCalendarService.getEventsInTimeRange(any(), any(), any()))
                .thenThrow(new IOException("network error"));

        assertThatThrownBy(() -> service.getAssignableEvents(USER_ID, date))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch assignable calendar events")
                .hasCauseInstanceOf(IOException.class);
    }

    // -------------------------------------------------------------------------
    // getEventComparison
    // -------------------------------------------------------------------------

    @Test
    void getEventComparison_onlyCalendarEvents_zeroActual() throws Exception {
        LocalDate from = LocalDate.of(2026, 5, 4);
        LocalDate to   = LocalDate.of(2026, 5, 10);
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end   = start.plusHours(2);

        when(googleOAuthTokenService.getValidAccessToken(USER_ID)).thenReturn(ACCESS_TOKEN);
        when(googleCalendarService.getEventsInTimeRange(any(), any(), any()))
                .thenReturn(List.of(calEvent("Deep Work", start, end)));
        when(activityRepository.findByUserIdAndDateBetweenAndCalendarEventTitleIsNotNull(USER_ID, from, to))
                .thenReturn(List.of());

        List<EventComparisonResponse> result = service.getEventComparison(USER_ID, from, to);

        assertThat(result).hasSize(1);
        EventComparisonResponse r = result.get(0);
        assertThat(r.eventTitle()).isEqualTo("Deep Work");
        assertThat(r.plannedSeconds()).isEqualTo(7200L);
        assertThat(r.actualSeconds()).isZero();
        assertThat(r.deficitSeconds()).isEqualTo(-7200L);
    }

    @Test
    void getEventComparison_onlyLinkedActivities_zeroPlanned() throws Exception {
        LocalDate from = LocalDate.of(2026, 5, 4);
        LocalDate to   = LocalDate.of(2026, 5, 10);

        when(googleOAuthTokenService.getValidAccessToken(USER_ID)).thenReturn(ACCESS_TOKEN);
        when(googleCalendarService.getEventsInTimeRange(any(), any(), any())).thenReturn(List.of());
        Activity a = activity(from, LocalTime.of(10, 0), LocalTime.of(11, 0), "evt-1", "Reading");
        when(activityRepository.findByUserIdAndDateBetweenAndCalendarEventTitleIsNotNull(USER_ID, from, to))
                .thenReturn(List.of(a));

        List<EventComparisonResponse> result = service.getEventComparison(USER_ID, from, to);

        assertThat(result).hasSize(1);
        EventComparisonResponse r = result.get(0);
        assertThat(r.eventTitle()).isEqualTo("Reading");
        assertThat(r.plannedSeconds()).isZero();
        assertThat(r.actualSeconds()).isEqualTo(3600L);
        assertThat(r.deficitSeconds()).isEqualTo(3600L);
    }

    @Test
    void getEventComparison_matchingBothSides_correctDeficit() throws Exception {
        LocalDate from = LocalDate.of(2026, 5, 4);
        LocalDate to   = LocalDate.of(2026, 5, 10);
        OffsetDateTime evStart = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime evEnd   = evStart.plusHours(3); // planned = 10800s

        when(googleOAuthTokenService.getValidAccessToken(USER_ID)).thenReturn(ACCESS_TOKEN);
        when(googleCalendarService.getEventsInTimeRange(any(), any(), any()))
                .thenReturn(List.of(calEvent("Exercise", evStart, evEnd)));
        // actual = 2 hours = 7200s
        Activity a = activity(from, LocalTime.of(6, 0), LocalTime.of(8, 0), "evt-1", "Exercise");
        when(activityRepository.findByUserIdAndDateBetweenAndCalendarEventTitleIsNotNull(USER_ID, from, to))
                .thenReturn(List.of(a));

        List<EventComparisonResponse> result = service.getEventComparison(USER_ID, from, to);

        assertThat(result).hasSize(1);
        EventComparisonResponse r = result.get(0);
        assertThat(r.plannedSeconds()).isEqualTo(10800L);
        assertThat(r.actualSeconds()).isEqualTo(7200L);
        assertThat(r.deficitSeconds()).isEqualTo(-3600L); // 7200 - 10800
    }

    @Test
    void getEventComparison_titleMatchingIsCaseInsensitive() throws Exception {
        LocalDate from = LocalDate.of(2026, 5, 4);
        LocalDate to   = LocalDate.of(2026, 5, 10);
        OffsetDateTime evStart = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime evEnd   = evStart.plusHours(1); // 3600s planned

        when(googleOAuthTokenService.getValidAccessToken(USER_ID)).thenReturn(ACCESS_TOKEN);
        // Calendar uses title-case
        when(googleCalendarService.getEventsInTimeRange(any(), any(), any()))
                .thenReturn(List.of(calEvent("Morning Run", evStart, evEnd)));
        // Activity uses all-uppercase
        Activity a = activity(from, LocalTime.of(7, 0), LocalTime.of(7, 30), "evt-1", "MORNING RUN");
        when(activityRepository.findByUserIdAndDateBetweenAndCalendarEventTitleIsNotNull(USER_ID, from, to))
                .thenReturn(List.of(a));

        List<EventComparisonResponse> result = service.getEventComparison(USER_ID, from, to);

        // Both map to the same key, so there is only one entry
        assertThat(result).hasSize(1);
        assertThat(result.get(0).plannedSeconds()).isEqualTo(3600L);
        assertThat(result.get(0).actualSeconds()).isEqualTo(1800L);
    }

    @Test
    void getEventComparison_calendarEventsWithNullFields_areSkipped() throws Exception {
        LocalDate from = LocalDate.of(2026, 5, 4);
        LocalDate to   = LocalDate.of(2026, 5, 10);
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);

        // null title, null startDateTime, null endDateTime — all three should be skipped
        CalendarEventResponse nullTitle     = new CalendarEventResponse("1", null,        null, null, start,  start.plusHours(1), "UTC", null, null);
        CalendarEventResponse nullStart     = new CalendarEventResponse("2", "No Start",  null, null, null,   start.plusHours(1), "UTC", null, null);
        CalendarEventResponse nullEnd       = new CalendarEventResponse("3", "No End",    null, null, start,  null,               "UTC", null, null);
        CalendarEventResponse valid         = new CalendarEventResponse("4", "Valid",      null, null, start,  start.plusHours(2), "UTC", null, null);

        when(googleOAuthTokenService.getValidAccessToken(USER_ID)).thenReturn(ACCESS_TOKEN);
        when(googleCalendarService.getEventsInTimeRange(any(), any(), any()))
                .thenReturn(List.of(nullTitle, nullStart, nullEnd, valid));
        when(activityRepository.findByUserIdAndDateBetweenAndCalendarEventTitleIsNotNull(USER_ID, from, to))
                .thenReturn(List.of());

        List<EventComparisonResponse> result = service.getEventComparison(USER_ID, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).eventTitle()).isEqualTo("Valid");
        assertThat(result.get(0).plannedSeconds()).isEqualTo(7200L);
    }

    @Test
    void getEventComparison_multipleOccurrencesSameTitle_summedCorrectly() throws Exception {
        LocalDate from = LocalDate.of(2026, 5, 4);
        LocalDate to   = LocalDate.of(2026, 5, 10);
        OffsetDateTime base = from.atStartOfDay().atOffset(ZoneOffset.UTC);

        // Three occurrences of "Standup", each 1 hour → 10800s planned
        CalendarEventResponse ev1 = calEvent("Standup", base,               base.plusHours(1));
        CalendarEventResponse ev2 = calEvent("Standup", base.plusDays(1),   base.plusDays(1).plusHours(1));
        CalendarEventResponse ev3 = calEvent("Standup", base.plusDays(2),   base.plusDays(2).plusHours(1));

        when(googleOAuthTokenService.getValidAccessToken(USER_ID)).thenReturn(ACCESS_TOKEN);
        when(googleCalendarService.getEventsInTimeRange(any(), any(), any()))
                .thenReturn(List.of(ev1, ev2, ev3));
        // Two activity sessions: 30m + 45m = 4500s actual
        Activity a1 = activity(from,            LocalTime.of(9, 0), LocalTime.of(9, 30),  "e1", "Standup");
        Activity a2 = activity(from.plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 45), "e2", "Standup");
        when(activityRepository.findByUserIdAndDateBetweenAndCalendarEventTitleIsNotNull(USER_ID, from, to))
                .thenReturn(List.of(a1, a2));

        List<EventComparisonResponse> result = service.getEventComparison(USER_ID, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).plannedSeconds()).isEqualTo(10800L);
        assertThat(result.get(0).actualSeconds()).isEqualTo(4500L);
        assertThat(result.get(0).deficitSeconds()).isEqualTo(4500L - 10800L);
    }

    @Test
    void getEventComparison_wrapsGeneralSecurityExceptionAsRuntimeException() throws Exception {
        LocalDate from = LocalDate.of(2026, 5, 4);
        LocalDate to   = LocalDate.of(2026, 5, 10);
        when(googleOAuthTokenService.getValidAccessToken(USER_ID)).thenReturn(ACCESS_TOKEN);
        when(googleCalendarService.getEventsInTimeRange(any(), any(), any()))
                .thenThrow(new GeneralSecurityException("ssl error"));

        assertThatThrownBy(() -> service.getEventComparison(USER_ID, from, to))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch calendar events")
                .hasCauseInstanceOf(GeneralSecurityException.class);
    }
}
