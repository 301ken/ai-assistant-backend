package com.ai.scheduler.service.llm;

public interface LlmStructuredClient {
    <T> T generateStructuredResponse(String prompt, Class<T> responseType);
}
