package com.ai.scheduler.service.llm_generic;

import com.ai.scheduler.dto.llm.TaskDTO;
import com.ai.scheduler.service.TaskExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TaskExtractionServiceTest {

    private TaskExtractionService taskExtractionService;

    @BeforeEach
    public void setUp(){

        String apiKey = System.getenv("OPENAI_API_KEY");

        assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI api key not found");
        OpenAiLlmClient openAiClient = new OpenAiLlmClient(new RestTemplate());
        ReflectionTestUtils.setField(openAiClient, "apiKey", apiKey);

        DefaultLlmStructuredClient defaultLlmStructuredClient = new DefaultLlmStructuredClient(openAiClient, new ObjectMapper());

        this.taskExtractionService = new TaskExtractionService(defaultLlmStructuredClient);
    }

    @Test
    public void generateStucturedResponse_ConvertsSimpleTextIntoTasks() {
        String prompt = "I have to study Math(10) and also do my programming assignment(7) and in addition to that go to gym(1) and also do leetcode(10) and I need a weekly schedule";

        List<TaskDTO> tasks = taskExtractionService.extractTasks(prompt);

        assumeTrue(tasks != null && !tasks.isEmpty(), "The service outputted an empty list");

        for (TaskDTO taskDTO : tasks) {
            System.out.println(taskDTO.title() + ": " + taskDTO.priority());
        }
    }
}
