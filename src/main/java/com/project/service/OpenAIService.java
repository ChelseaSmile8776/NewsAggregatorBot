package com.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String summarize(String text, String systemPrompt) {
        if (apiKey == null || apiKey.isEmpty()) return null;

        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4o-mini");

        // Усиленный промпт
        String strictPrompt = systemPrompt +
                "\n\nИНСТРУКЦИЯ:\n" +
                "1. Верни ТОЛЬКО готовый текст поста. Без вступлений типа 'Вот саммари'.\n" +
                "2. Если текст - мусор/реклама/спам -> верни слово SKIP.\n" +
                "3. Используй HTML-теги: <b>Заголовок</b>, <i>акценты</i>.\n" +
                "4. Структура: Заголовок (жирным) -> Пустая строка -> Суть -> 2-3 хештега.";

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", strictPrompt),
                Map.of("role", "user", "content", text)
        );

        body.put("messages", messages);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Ошибка OpenAI: {}", e.getMessage());
            return null;
        }
    }
}
