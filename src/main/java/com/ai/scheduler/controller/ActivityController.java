package com.ai.scheduler.controller;

import com.ai.scheduler.dto.activity.ActivityRequest;
import com.ai.scheduler.dto.activity.ActivityResponse;
import com.ai.scheduler.dto.activity.ActivityStatsResponse;
import com.ai.scheduler.dto.activity.EventComparisonResponse;
import com.ai.scheduler.dto.calendar.google_calendar.CalendarEventResponse;
import com.ai.scheduler.entity.ActivityType;
import com.ai.scheduler.service.ActivityService;
import com.ai.scheduler.service.ActivityStatisticsService;
import com.ai.scheduler.util.SecurityUtils;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;
    private final ActivityStatisticsService activityStatisticsService;

    public ActivityController(ActivityService activityService,
                              ActivityStatisticsService activityStatisticsService) {
        this.activityService = activityService;
        this.activityStatisticsService = activityStatisticsService;
    }

    @GetMapping
    public ResponseEntity<List<ActivityResponse>> list(
            @RequestParam(required = false) ActivityType activityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(activityService.list(SecurityUtils.currentUserId(), activityType, date));
    }

    @PostMapping
    public ResponseEntity<ActivityResponse> create(@Valid @RequestBody ActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activityService.create(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(activityService.get(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ActivityResponse> update(@PathVariable Long id, @Valid @RequestBody ActivityRequest request) {
        return ResponseEntity.ok(activityService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        activityService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns Google Calendar events in a ±1-day window around {@code date}.
     * Used by the frontend to populate the "assign to event" picker after stopping the timer.
     *
     * GET /api/activities/assignable-events?date=2026-05-07
     */
    @GetMapping("/assignable-events")
    public ResponseEntity<List<CalendarEventResponse>> assignableEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(
                activityStatisticsService.getAssignableEvents(SecurityUtils.currentUserId(), date));
    }

    /**
     * Returns a planned-vs-actual breakdown per event title within the given date range.
     *
     * GET /api/activities/stats/event-comparison?from=2026-05-04&to=2026-05-10
     */
    @GetMapping("/stats/event-comparison")
    public ResponseEntity<List<EventComparisonResponse>> eventComparison(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                activityStatisticsService.getEventComparison(SecurityUtils.currentUserId(), from, to));
    }
}
