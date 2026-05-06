package com.ai.scheduler.service;

import com.ai.scheduler.dto.llm.TaskDTO;
import com.ai.scheduler.dto.llm.TaskListDTO;
import com.ai.scheduler.service.llm_generic.LlmStructuredClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskExtractionService {

    private LlmStructuredClient llmStructuredClient;

    public TaskExtractionService(LlmStructuredClient llmStructuredClient) {
        this.llmStructuredClient = llmStructuredClient;
    }

    public List<TaskDTO> extractTasks(String userInput) {
        String prompt = """
                Extract tasks from the following text.
                
                Text:
                %s
                
                Output format:
                {
                    "tasks": [
                        "title": "string",
                        "priority": number(1 to 10)
                    ]
                }
                """.formatted(userInput);

        TaskListDTO taskListDTO = llmStructuredClient.generateStructuredResponse(prompt, TaskListDTO.class);
        return taskListDTO.tasks();
    }
}
