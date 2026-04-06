package com.ai.scheduler.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank String token) {
}
