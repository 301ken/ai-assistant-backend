package com.ai.scheduler.service;

import com.ai.scheduler.dto.user.UpdateUserRequest;
import com.ai.scheduler.dto.user.UserResponse;
import com.ai.scheduler.entity.User;
import com.ai.scheduler.exception.ResourceNotFoundException;
import com.ai.scheduler.repository.ActivityRepository;
import com.ai.scheduler.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;

    public UserService(UserRepository userRepository, ActivityRepository activityRepository) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
    }

    public UserResponse getMe(Long userId) {
        return toResponse(getUserEntity(userId));
    }

    @Transactional
    public UserResponse updateMe(Long userId, UpdateUserRequest request) {
        User user = getUserEntity(userId);
        user.setName(request.name());
        return toResponse(user);
    }

    @Transactional
    public void deleteMe(Long userId) {
        activityRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }

    public User getUserEntity(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.isAccountActivated(), user.getCreatedAt());
    }
}
