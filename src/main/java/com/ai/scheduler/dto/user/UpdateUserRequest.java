package com.ai.scheduler.dto.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(@NotBlank String name) {
}
