package com.ai.scheduler.service.llm_generic;

public interface LlmStructuredClient {
    <T> T generateStructuredResponse(String prompt, Class<T> responseType);
}
