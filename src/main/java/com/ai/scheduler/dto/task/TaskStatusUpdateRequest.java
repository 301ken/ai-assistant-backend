package com.ai.scheduler.dto.task;

import com.ai.scheduler.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusUpdateRequest(@NotNull TaskStatus status) {
}
