package com.ai.scheduler.service;

import com.ai.scheduler.dto.calendar.ScheduleConstraint;
import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventRequest;
import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventResponse;
import com.ai.scheduler.dto.calendar.scheduler.GeneratedSchedule;
import com.ai.scheduler.dto.calendar.scheduler.SchedulerEvent;
import com.ai.scheduler.dto.calendar.scheduler.SchedulingRequest;
import com.ai.scheduler.dto.llm.TaskDTO;
import com.ai.scheduler.dto.llm.TaskListDTO;
import com.ai.scheduler.dto.time.TimeRange;
import com.ai.scheduler.schedulers.Scheduler;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SchedulingWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(SchedulingWorkflowService.class);

    private static final String DEFAULT_SCHEDULER = "proportional";

    private final GoogleCalendarService googleCalendarService;
    private final GoogleOAuthTokenService googleOAuthTokenService;
    private final TaskExtractionService taskExtractionService;
    private final Map<String, Scheduler> schedulers;

    public SchedulingWorkflowService(GoogleCalendarService googleCalendarService,
                                     GoogleOAuthTokenService googleOAuthTokenService,
                                     TaskExtractionService taskExtractionService,
                                     Map<String, Scheduler> schedulers) {
        this.googleCalendarService = googleCalendarService;
        this.googleOAuthTokenService = googleOAuthTokenService;
        this.taskExtractionService = taskExtractionService;
        this.schedulers = schedulers;
    }

    private Scheduler resolveScheduler(String type) {
        String key = (type == null || type.isBlank()) ? DEFAULT_SCHEDULER : type.trim().toLowerCase();
        Scheduler s = schedulers.get(key);
        if (s == null) {
            throw new IllegalArgumentException(
                    "Unknown schedulerType '" + key + "'. Available: " + schedulers.keySet());
        }
        return s;
    }

    private List<ScheduleConstraint> getConstraintsInTimeRange(Long userId, TimeRange timeRange) {
        String accessToken = googleOAuthTokenService.getValidAccessToken(userId);

        // Collect every date tыщ hat falls within the range (inclusive)
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = timeRange.from().toLocalDate();
        LocalDate last = timeRange.to().toLocalDate();
        while (!cursor.isAfter(last)) {
            days.add(cursor);
            cursor = cursor.plusDays(1);
        }

        try {
            return googleCalendarService
                    .getEventsForDays(accessToken, days, "UTC")
                    .stream()
                    .filter(e -> e.startDateTime() != null && e.endDateTime() != null)
                    .map(e -> new ScheduleConstraint(e.startDateTime(), e.endDateTime()))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Google Calendar events: " + e.getMessage(), e);
        }
    }

    public List<CalendarEventResponse> generateSchedule(Long userId, String prompt, TimeRange timeRange, double percentageOfTimeToUse, boolean recurrent, String schedulerType) {

        List<ScheduleConstraint> constraints = getConstraintsInTimeRange(userId, timeRange);
        List<TaskDTO> tasks = taskExtractionService.extractTasks(prompt);
        SchedulingRequest request =
                new SchedulingRequest(
                        new TaskListDTO(tasks),
                        constraints,
                        timeRange,
                        percentageOfTimeToUse,
                        recurrent);

        GeneratedSchedule schedule = resolveScheduler(schedulerType).generate(request);

        String accessToken = googleOAuthTokenService.getValidAccessToken(userId);
        List<CalendarEventResponse> created = new ArrayList<>();
        for (SchedulerEvent event : schedule.events()) {
            CalendarEventRequest calRequest = new CalendarEventRequest(
                    event.title(),
                    event.description(),
                    event.color(),
                    event.startDateTime(),
                    event.endDateTime(),
                    event.timeZone(),
                    event.recurrence()
            );
            try {
                CalendarEventResponse response = googleCalendarService.createEvent(accessToken, calRequest);
                created.add(response);
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException("Failed to create calendar event '" + event.title() + "': " + e.getMessage(), e);
            }
        }
        return created;
    }

    /**
     * Deletes the given calendar events — typically the ones returned by a preceding
     * {@link #generateSchedule} call, whose IDs the caller passes back in.
     *
     * <p>Stateless by design: the IDs to delete come from the request rather than a
     * server-side field, so concurrent users can never roll back each other's events.
     * Safe to call with an empty list (no-op).</p>
     *
     * @param userId   the authenticated user whose calendar tokens are used
     * @param eventIds the calendar event IDs to delete
     */
    public void rollbackLastSchedule(Long userId, List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        String accessToken = googleOAuthTokenService.getValidAccessToken(userId);
        List<String> failed = new ArrayList<>();
        for (String eventId : eventIds) {
            try {
                googleCalendarService.deleteEvent(accessToken, eventId);
                log.info("Rolled back calendar event: {}", eventId);
            } catch (IOException | GeneralSecurityException e) {
                log.error("Failed to delete event {} during rollback: {}", eventId, e.getMessage());
                failed.add(eventId);
            }
        }
        if (!failed.isEmpty()) {
            throw new RuntimeException("Rollback incomplete — could not delete event IDs: " + failed);
        }
    }
}
