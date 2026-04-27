package com.ai.scheduler.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DefaultLlmStructuredClient implements LlmStructuredClient{

    private LlmClient llmClient;
    private ObjectMapper objectMapper;

    public DefaultLlmStructuredClient(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T generateStructuredResponse(String prompt, Class<T> responseType) {
        String enhancedPrompt = prompt + """
                IMPORTANT:
                Return ONLY valid JSON.
                DO NOT include explanations or text outside JSON.
                """;

        String raw = llmClient.generateText(prompt);

        int firstIndex = raw.indexOf("{");
        int lastIndex = raw.lastIndexOf("}");

        String json = raw.substring(firstIndex, lastIndex + 1);

        try {
            return objectMapper.readValue(json, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM response: " + raw, e);
        }
    }
}
