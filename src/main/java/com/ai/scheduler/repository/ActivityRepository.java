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
    List<Activity> findByTaskIdAndUserId(Long taskId, Long userId);
    Optional<Activity> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
