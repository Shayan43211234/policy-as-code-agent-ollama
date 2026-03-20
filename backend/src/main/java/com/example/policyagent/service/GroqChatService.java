package com.example.policyagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
public class GroqChatService {

    private static final Logger logger = LoggerFactory.getLogger(GroqChatService.class);

    private final WebClient webClient;

    @Value("${groq.api-key}")
    private String apiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    public GroqChatService(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://api.groq.com/openai/v1")
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    public String chat(String prompt) {

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 4096,
                "temperature", 0.1
        );

        logger.info("Calling Groq API with model: {}", model);

        try {
            JsonNode response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String result = response
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            logger.debug("Groq response (first 300 chars): {}",
                    result.length() > 300 ? result.substring(0, 300) + "..." : result);

            return result;

        } catch (WebClientResponseException e) {
            logger.error("Groq API error - Status: {} - Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Groq API call failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("Groq API call failed: {}", e.getMessage());
            throw new RuntimeException("Groq API call failed", e);
        }
    }
}