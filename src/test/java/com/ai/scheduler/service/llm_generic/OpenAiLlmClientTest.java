package com.ai.scheduler.service.llm_generic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenAiLlmClientTest {

    private OpenAiLlmClient client;

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY env var not set — skipping integration test");

        client = new OpenAiLlmClient(new RestTemplate());
        ReflectionTestUtils.setField(client, "apiKey", apiKey);
    }

    @Test
    void generateText_returnsNonEmptyResponse() {
        String result = client.generateText("Say just the word hello, nothing else.");

        assertThat(result).isNotBlank();
        System.out.println("Response from OpenAI: " + result);
    }

    @Test
    void generateText_respondsToSimpleMathQuestion() {
        String result = client.generateText("What is 2 + 2? Reply with only the number.");

        assertThat(result).isNotBlank();
        assertThat(result.trim()).contains("4");
        System.out.println("Response from OpenAI: " + result);
    }
}
