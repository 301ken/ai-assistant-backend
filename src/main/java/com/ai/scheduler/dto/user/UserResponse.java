package com.ai.scheduler.dto.user;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String name,
        String email,
        boolean accountActivated,
        LocalDateTime createdAt
) {
}
