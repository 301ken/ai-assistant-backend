package com.ai.scheduler.service;

import com.ai.scheduler.dto.task.TaskRequest;
import com.ai.scheduler.dto.task.TaskResponse;
import com.ai.scheduler.entity.Task;
import com.ai.scheduler.entity.TaskStatus;
import com.ai.scheduler.entity.User;
import com.ai.scheduler.exception.ResourceNotFoundException;
import com.ai.scheduler.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserService userService;

    public TaskService(TaskRepository taskRepository, UserService userService) {
        this.taskRepository = taskRepository;
        this.userService = userService;
    }

    public List<TaskResponse> list(Long userId, TaskStatus status, LocalDate date) {
        List<Task> tasks;
        if (status != null) {
            tasks = taskRepository.findByUserIdAndStatus(userId, status);
        } else if (date != null) {
            tasks = taskRepository.findByUserIdAndDate(userId, date);
        } else {
            tasks = taskRepository.findByUserId(userId);
        }
        return tasks.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TaskResponse create(Long userId, TaskRequest request) {
        User user = userService.getUserEntity(userId);
        Task task = new Task();
        task.setUser(user);
        apply(task, request);
        if (request.status() == null) {
            task.setStatus(TaskStatus.PENDING);
        }
        return toResponse(taskRepository.save(task));
    }

    public TaskResponse get(Long userId, Long taskId) {
        return toResponse(getEntity(userId, taskId));
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, TaskRequest request) {
        Task task = getEntity(userId, taskId);
        apply(task, request);
        if (request.status() == null) {
            task.setStatus(TaskStatus.PENDING);
        }
        return toResponse(task);
    }

    @Transactional
    public TaskResponse updateStatus(Long userId, Long taskId, TaskStatus status) {
        Task task = getEntity(userId, taskId);
        task.setStatus(status);
        return toResponse(task);
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        Task task = getEntity(userId, taskId);
        taskRepository.delete(task);
    }

    public Task getEntity(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private void apply(Task task, TaskRequest request) {
        task.setTitle(request.title());
        task.setPriorityWeight(request.priorityWeight());
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        task.setDate(request.date());
        task.setStartTime(request.startTime());
        task.setEndTime(request.endTime());
        task.setRepeatWeekly(request.repeatWeekly());
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getUser().getId(), task.getTitle(), task.getPriorityWeight(),
                task.getStatus(), task.getDate(), task.getStartTime(), task.getEndTime(), task.isRepeatWeekly(),
                task.getCreatedAt());
    }
}
