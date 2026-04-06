package com.ai.scheduler.service;

import com.ai.scheduler.dto.activity.ActivityRequest;
import com.ai.scheduler.dto.activity.ActivityResponse;
import com.ai.scheduler.entity.Activity;
import com.ai.scheduler.entity.ActivityType;
import com.ai.scheduler.entity.Task;
import com.ai.scheduler.entity.User;
import com.ai.scheduler.exception.ResourceNotFoundException;
import com.ai.scheduler.repository.ActivityRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserService userService;
    private final TaskService taskService;

    public ActivityService(ActivityRepository activityRepository, UserService userService, TaskService taskService) {
        this.activityRepository = activityRepository;
        this.userService = userService;
        this.taskService = taskService;
    }

    public List<ActivityResponse> list(Long userId, ActivityType activityType, LocalDate date) {
        List<Activity> items;
        if (activityType != null) {
            items = activityRepository.findByUserIdAndActivityType(userId, activityType);
        } else if (date != null) {
            items = activityRepository.findByUserIdAndDate(userId, date);
        } else {
            items = activityRepository.findByUserId(userId);
        }
        return items.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ActivityResponse create(Long userId, ActivityRequest request) {
        User user = userService.getUserEntity(userId);
        Activity activity = new Activity();
        activity.setUser(user);
        apply(userId, activity, request);
        return toResponse(activityRepository.save(activity));
    }

    public ActivityResponse get(Long userId, Long id) {
        return toResponse(getEntity(userId, id));
    }

    @Transactional
    public ActivityResponse update(Long userId, Long id, ActivityRequest request) {
        Activity activity = getEntity(userId, id);
        apply(userId, activity, request);
        return toResponse(activity);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        activityRepository.delete(getEntity(userId, id));
    }

    public List<ActivityResponse> listByTask(Long userId, Long taskId) {
        taskService.getEntity(userId, taskId);
        return activityRepository.findByTaskIdAndUserId(taskId, userId).stream().map(this::toResponse).toList();
    }

    private Activity getEntity(Long userId, Long id) {
        return activityRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));
    }

    private void apply(Long userId, Activity activity, ActivityRequest request) {
        Task task = null;
        if (request.taskId() != null) {
            task = taskService.getEntity(userId, request.taskId());
        }
        activity.setTask(task);
        activity.setActivityType(request.activityType());
        activity.setDescription(request.activityDescription());
        activity.setDate(request.date());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
    }

    private ActivityResponse toResponse(Activity activity) {
        Long taskId = activity.getTask() != null ? activity.getTask().getId() : null;
        return new ActivityResponse(activity.getId(), activity.getUser().getId(), taskId,
                activity.getActivityType(), activity.getDescription(), activity.getDate(),
                activity.getStartTime(), activity.getEndTime());
    }
}
