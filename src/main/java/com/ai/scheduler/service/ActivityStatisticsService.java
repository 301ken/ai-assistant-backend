package com.ai.scheduler.service;

import com.ai.scheduler.dto.activity.ActivityStatsResponse;
import com.ai.scheduler.dto.activity.EventComparisonResponse;
import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventResponse;
import com.ai.scheduler.entity.Activity;
import com.ai.scheduler.repository.ActivityRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ActivityStatisticsService {

    private final ActivityRepository activityRepository;
    private final GoogleCalendarService googleCalendarService;
    private final GoogleOAuthTokenService googleOAuthTokenService;

    public ActivityStatisticsService(ActivityRepository activityRepository,
                                     GoogleCalendarService googleCalendarService,
                                     GoogleOAuthTokenService googleOAuthTokenService) {
        this.activityRepository = activityRepository;
        this.googleCalendarService = googleCalendarService;
        this.googleOAuthTokenService = googleOAuthTokenService;
    }

    public ActivityStatsResponse calculateStatistics(Long userId, LocalDate from, LocalDate to) {
        List<Activity> activities = activityRepository.findByUserIdAndDateBetween(userId, from, to);
        long totalSeconds = activities.stream()
                .mapToLong(a -> duration(a.getStartTime(), a.getEndTime())).sum();
        double totalHours = totalSeconds / 3600.0;
        int sessionCount = activities.size();

        List<ActivityStatsResponse.DailyBreakDown> dailyBreakdown = activities.stream()
                .collect(java.util.stream.Collectors.groupingBy(Activity::getDate))
                .entrySet().stream()
                .map(entry -> new ActivityStatsResponse.DailyBreakDown(
                        entry.getKey(),
                        entry.getValue().stream()
                                .mapToLong(a -> duration(a.getStartTime(), a.getEndTime())).sum()))
                .toList();

        return new ActivityStatsResponse(totalSeconds, totalHours, sessionCount, dailyBreakdown);
    }

    /**
     * Returns a list of Google Calendar events in a ±1-day window around {@code date},
     * intended for the "assign activity to event" picker.
     */
    public List<CalendarEventResponse> getAssignableEvents(Long userId, LocalDate date) {
        String accessToken = googleOAuthTokenService.getValidAccessToken(userId);
        OffsetDateTime from = date.minusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to   = date.plusDays(2).atStartOfDay().atOffset(ZoneOffset.UTC);
        try {
            return googleCalendarService.getEventsInTimeRange(accessToken, from, to);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to fetch assignable calendar events: " + e.getMessage(), e);
        }
    }

    /**
     * Compares planned time (from Google Calendar events) vs actual time (from linked
     * activity sessions) for each event title within the given date range.
     *
     * <ul>
     *   <li>Planned = sum of (endTime − startTime) for every calendar event whose title matches.</li>
     *   <li>Actual  = sum of activity session durations that have {@code calendarEventTitle} set.</li>
     *   <li>Titles are matched case-insensitively; the original casing from the calendar is kept.</li>
     * </ul>
     */
    public List<EventComparisonResponse> getEventComparison(Long userId, LocalDate from, LocalDate to) {
        // --- Planned side: Google Calendar ---
        String accessToken = googleOAuthTokenService.getValidAccessToken(userId);
        OffsetDateTime calFrom = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime calTo   = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<CalendarEventResponse> calEvents;
        try {
            calEvents = googleCalendarService.getEventsInTimeRange(accessToken, calFrom, calTo);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException("Failed to fetch calendar events: " + e.getMessage(), e);
        }

        Map<String, String> originalTitle = new LinkedHashMap<>();
        Map<String, Long> plannedSeconds  = new LinkedHashMap<>();
        for (CalendarEventResponse ev : calEvents) {
            if (ev.title() == null || ev.startDateTime() == null || ev.endDateTime() == null) continue;
            String key = ev.title().toLowerCase();
            originalTitle.putIfAbsent(key, ev.title());
            long seconds = Duration.between(ev.startDateTime(), ev.endDateTime()).getSeconds();
            plannedSeconds.merge(key, seconds, Long::sum);
        }

        // --- Actual side: Activity DB ---
        List<Activity> linked = activityRepository
                .findByUserIdAndDateBetweenAndCalendarEventTitleIsNotNull(userId, from, to);

        Map<String, Long> actualSeconds = new LinkedHashMap<>();
        for (Activity a : linked) {
            String key = a.getCalendarEventTitle().toLowerCase();
            originalTitle.putIfAbsent(key, a.getCalendarEventTitle());
            actualSeconds.merge(key, duration(a.getStartTime(), a.getEndTime()), Long::sum);
        }

        // --- Merge ---
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(plannedSeconds.keySet());
        allKeys.addAll(actualSeconds.keySet());

        return allKeys.stream().map(key -> {
            long planned = plannedSeconds.getOrDefault(key, 0L);
            long actual  = actualSeconds.getOrDefault(key, 0L);
            return new EventComparisonResponse(originalTitle.get(key), planned, actual, actual - planned);
        }).toList();
    }

    private long duration(LocalTime start, LocalTime end) {
        return Duration.between(start, end).getSeconds();
    }
}
