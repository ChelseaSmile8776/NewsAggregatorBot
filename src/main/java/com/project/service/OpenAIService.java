package com.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.config.BotConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAIService {

    private final BotConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper(); // Jackson для JSON

    public String generateSummary(String newsText, String systemPrompt) {
        String url = "https://api.openai.com/v1/chat/completions";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(url);
            request.setHeader("Authorization", "Bearer " + config.getOpenaiToken());
            request.setHeader("Content-Type", "application/json");

            // Формируем JSON тела запроса вручную или через объект (тут проще строкой для наглядности)
            // Но лучше использовать ObjectNode, чтобы экранировать кавычки в тексте новости!
            var jsonBody = objectMapper.createObjectNode();
            jsonBody.put("model", "gpt-4o-mini"); // Или gpt-3.5-turbo

            var messages = objectMapper.createArrayNode();
            messages.add(objectMapper.createObjectNode().put("role", "system").put("content", systemPrompt));
            messages.add(objectMapper.createObjectNode().put("role", "user").put("content", "Сделай пост для Telegram из этого текста. Оставь только суть, добавь смайлики. Текст: " + newsText));

            jsonBody.set("messages", messages);

            request.setEntity(new StringEntity(jsonBody.toString(), ContentType.APPLICATION_JSON));

            return httpClient.execute(request, response -> {
                String responseBody = new String(response.getEntity().getContent().readAllBytes());
                JsonNode root = objectMapper.readTree(responseBody);

                if (root.has("error")) {
                    log.error("OpenAI Error: {}", root.get("error").toPrettyString());
                    return null;
                }

                // Достаем текст ответа
                return root.path("choices").get(0).path("message").path("content").asText();
            });

        } catch (IOException e) {
            log.error("Ошибка при запросе к OpenAI: {}", e.getMessage());
            return null;
        }
    }
}
