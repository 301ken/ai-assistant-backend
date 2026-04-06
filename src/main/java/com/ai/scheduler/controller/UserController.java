package com.ai.scheduler.controller;

import com.ai.scheduler.dto.user.UpdateUserRequest;
import com.ai.scheduler.dto.user.UserResponse;
import com.ai.scheduler.service.UserService;
import com.ai.scheduler.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(userService.getMe(SecurityUtils.currentUserId()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateMe(SecurityUtils.currentUserId(), request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe() {
        userService.deleteMe(SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
