package com.ai.scheduler.repository;

import com.ai.scheduler.entity.Task;
import com.ai.scheduler.entity.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByUserId(Long userId);
    List<Task> findByUserIdAndStatus(Long userId, TaskStatus status);
    List<Task> findByUserIdAndDate(Long userId, LocalDate date);
    Optional<Task> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
