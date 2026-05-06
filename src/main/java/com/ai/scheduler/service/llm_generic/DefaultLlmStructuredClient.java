package com.ai.scheduler.service.llm_generic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

@Service
public class DefaultLlmStructuredClient implements LlmStructuredClient{

    private LlmClient llmClient;
    private ObjectMapper objectMapper;

    public DefaultLlmStructuredClient(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

    @Override
    public <T> T generateStructuredResponse(String prompt, Class<T> responseType) {
        String enhancedPrompt = prompt + """
                IMPORTANT:
                Return ONLY valid JSON.
                DO NOT include explanations or text outside JSON.
                """;

        String raw = llmClient.generateText(enhancedPrompt);

        System.out.println("Raw-----------------------------------------------");
        System.out.println(raw);
        System.out.println("--------------------------------------------------");

        int firstIndex = raw.indexOf("{");
        int lastIndex = raw.lastIndexOf("}");

        String json = raw.substring(firstIndex, lastIndex + 1);
        json = sanitizeJson(json);

        try {
            return objectMapper.readValue(json, responseType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM response: " + raw, e);
        }
    }

    private String sanitizeJson(String raw) {
        return raw
                // remove non-breaking spaces
                .replace('\u00A0', ' ')
                // remove weird unicode control chars
                .replaceAll("[\\u0000-\\u001F]", "")
                // trim
                .trim();
    }
}
