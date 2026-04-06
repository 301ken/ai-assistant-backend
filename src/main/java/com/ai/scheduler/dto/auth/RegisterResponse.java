package com.ai.scheduler.dto.auth;

public record RegisterResponse(
        Long id,
        String name,
        String email,
        boolean accountActivated,
        String message
) {
}
