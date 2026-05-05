package com.ai.scheduler.dto.llm;

import java.util.List;

public record TaskListDTO (
        List<TaskDTO> tasks
){
}
