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
                Extract tasks from the following text and assess each task's properties.

                Text:
                %s

                For each task determine:
                  - title: a concise name for the task
                  - priority: urgency/importance score from 1 (lowest) to 10 (highest)
                  - cognitiveLoad: mental effort required from 1 (effortless, e.g. replying to a casual email)
                    to 10 (extremely demanding, e.g. writing a complex algorithm or exam preparation).
                    Use this scale:
                      1-2  = routine/mechanical (filing, simple data entry)
                      3-4  = light thinking (reading, attending a meeting)
                      5-6  = moderate focus (coding a feature, writing a report)
                      7-8  = deep work (complex debugging, research, designing architecture)
                      9-10 = maximum cognitive intensity (competitive exam, novel creative synthesis)

                Output format (strict JSON, no extra text):
                {
                    "tasks": [
                        {
                            "title": "string",
                            "priority": number between 1 and 10,
                            "cognitiveLoad": number between 1 and 10
                        }
                    ]
                }
                """.formatted(userInput);

        TaskListDTO taskListDTO = llmStructuredClient.generateStructuredResponse(prompt, TaskListDTO.class);
        return taskListDTO.tasks();
    }
}
