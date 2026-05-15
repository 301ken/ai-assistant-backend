package com.ai.scheduler.dto.llm;

/**
 * Represents a single extracted task.
 * <ul>
 *   <li>{@code title}         – human-readable task name</li>
 *   <li>{@code priority}      – importance score 1–10 (10 = most urgent)</li>
 *   <li>{@code cognitiveLoad} – mental effort required 1–10 (10 = most demanding)</li>
 * </ul>
 */
public record TaskDTO(
        String title,
        Double priority,
        Double cognitiveLoad
) {}
