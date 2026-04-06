package com.ai.scheduler.controller;

import com.ai.scheduler.dto.activity.ActivityResponse;
import com.ai.scheduler.dto.task.TaskRequest;
import com.ai.scheduler.dto.task.TaskResponse;
import com.ai.scheduler.dto.task.TaskStatusUpdateRequest;
import com.ai.scheduler.entity.TaskStatus;
import com.ai.scheduler.service.ActivityService;
import com.ai.scheduler.service.TaskService;
import com.ai.scheduler.util.SecurityUtils;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final ActivityService activityService;

    public TaskController(TaskService taskService, ActivityService activityService) {
        this.taskService = taskService;
        this.activityService = activityService;
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(taskService.list(SecurityUtils.currentUserId(), status, date));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.get(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(taskService.update(SecurityUtils.currentUserId(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateStatus(SecurityUtils.currentUserId(), id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/activities")
    public ResponseEntity<List<ActivityResponse>> listActivitiesForTask(@PathVariable Long id) {
        return ResponseEntity.ok(activityService.listByTask(SecurityUtils.currentUserId(), id));
    }
}
