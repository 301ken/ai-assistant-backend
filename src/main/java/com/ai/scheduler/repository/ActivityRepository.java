package com.ai.scheduler.repository;

import com.ai.scheduler.entity.Activity;
import com.ai.scheduler.entity.ActivityType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByUserId(Long userId);
    List<Activity> findByUserIdAndActivityType(Long userId, ActivityType activityType);
    List<Activity> findByUserIdAndDate(Long userId, LocalDate date);
    Optional<Activity> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
    List<Activity> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
    List<Activity> findByUserIdAndDateBetweenAndCalendarEventTitleIsNotNull(Long userId, LocalDate startDate, LocalDate endDate);
}
