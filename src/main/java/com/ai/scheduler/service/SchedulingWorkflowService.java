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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SchedulingWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(SchedulingWorkflowService.class);

    private final GoogleCalendarService googleCalendarService;
    private final GoogleOAuthTokenService googleOAuthTokenService;
    private final TaskExtractionService taskExtractionService;
    private final Scheduler scheduler;

    /** IDs of events created during the last generateSchedule call — used for rollback. */
    private final Set<String> lastCreatedEventIds = new LinkedHashSet<>();

    public SchedulingWorkflowService(GoogleCalendarService googleCalendarService,
                                     GoogleOAuthTokenService googleOAuthTokenService,
                                     TaskExtractionService taskExtractionService,
                                     Scheduler scheduler) {
        this.googleCalendarService = googleCalendarService;
        this.googleOAuthTokenService = googleOAuthTokenService;
        this.taskExtractionService = taskExtractionService;
        this.scheduler = scheduler;
    }

    private List<ScheduleConstraint> getConstraintsInTimeRange(Long userId, TimeRange timeRange) {
        String accessToken = googleOAuthTokenService.getValidAccessToken(userId);

        // Collect every date that falls within the range (inclusive)
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

    public List<CalendarEventResponse> generateSchedule(Long userId, String prompt, TimeRange timeRange, double percentageOfTimeToUse, boolean recurrent) {

        List<ScheduleConstraint> constraints = getConstraintsInTimeRange(userId, timeRange);
        List<TaskDTO> tasks = taskExtractionService.extractTasks(prompt);
        SchedulingRequest request =
                new SchedulingRequest(
                        new TaskListDTO(tasks),
                        constraints,
                        timeRange,
                        percentageOfTimeToUse,
                        recurrent);

        GeneratedSchedule schedule = scheduler.generate(request);

        String accessToken = googleOAuthTokenService.getValidAccessToken(userId);
        lastCreatedEventIds.clear();
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
                if (response.id() != null) {
                    lastCreatedEventIds.add(response.id());
                }
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException("Failed to create calendar event '" + event.title() + "': " + e.getMessage(), e);
            }
        }
        return created;
    }

    /**
     * Deletes all events that were created during the last {@link #generateSchedule} call.
     * Safe to call multiple times — subsequent calls will be no-ops if already rolled back.
     *
     * @param userId the authenticated user whose calendar tokens are used
     */
    public void rollbackLastSchedule(Long userId) {
        if (lastCreatedEventIds.isEmpty()) {
            return;
        }
        String accessToken = googleOAuthTokenService.getValidAccessToken(userId);
        List<String> failed = new ArrayList<>();
        for (String eventId : lastCreatedEventIds) {
            try {
                googleCalendarService.deleteEvent(accessToken, eventId);
                log.info("Rolled back calendar event: {}", eventId);
            } catch (IOException | GeneralSecurityException e) {
                log.error("Failed to delete event {} during rollback: {}", eventId, e.getMessage());
                failed.add(eventId);
            }
        }
        lastCreatedEventIds.clear();
        if (!failed.isEmpty()) {
            throw new RuntimeException("Rollback incomplete — could not delete event IDs: " + failed);
        }
    }
}
