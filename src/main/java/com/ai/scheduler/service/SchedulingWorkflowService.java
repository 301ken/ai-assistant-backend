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
import org.springframework.stereotype.Service;

@Service
public class SchedulingWorkflowService {

    private final GoogleCalendarService googleCalendarService;
    private final GoogleOAuthTokenService googleOAuthTokenService;
    private final TaskExtractionService taskExtractionService;
    private final Scheduler scheduler;

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
                created.add(googleCalendarService.createEvent(accessToken, calRequest));
            } catch (IOException | GeneralSecurityException e) {
                throw new RuntimeException("Failed to create calendar event '" + event.title() + "': " + e.getMessage(), e);
            }
        }
        return created;
    }
}
