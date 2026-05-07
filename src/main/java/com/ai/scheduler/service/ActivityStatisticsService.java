package com.ai.scheduler.service;

import com.ai.scheduler.dto.activity.ActivityStatsResponse;
import com.ai.scheduler.entity.Activity;
import com.ai.scheduler.repository.ActivityRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class ActivityStatisticsService {

    private ActivityRepository activityRepository;

    public ActivityStatisticsService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public ActivityStatsResponse calculateStatistics(Long userId, LocalDate from, LocalDate to) {

        List<Activity> activities = activityRepository.findByUserIdAndDateBetween(userId, from, to);
        long totalSeconds = activities.stream().mapToLong(activity -> duration(activity.getStartTime(), activity.getEndTime())).sum();
        double totalHours = totalSeconds / 3600.0;
        int sessionCount = activities.size();

        List<ActivityStatsResponse.DailyBreakDown> dailyBreakdown = activities.stream()
                .collect(java.util.stream.Collectors.groupingBy(Activity::getDate))
                .entrySet().stream()
                .map(entry -> new ActivityStatsResponse.DailyBreakDown(
                        entry.getKey(),
                        entry.getValue().stream().mapToLong(activity -> duration(activity.getStartTime(), activity.getEndTime())).sum()
                )).toList();

        return new ActivityStatsResponse(totalSeconds, totalHours, sessionCount, dailyBreakdown);
    }

    private long duration(LocalTime start, LocalTime end) {
        return java.time.Duration.between(start, end).getSeconds();
    }
}
