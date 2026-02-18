package com.project.service;

import com.project.entity.Source;
import com.project.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParserService {

    private final SourceRepository sourceRepository;

    public List<String> parseNewPosts(Source source) {
        List<String> newPosts = new ArrayList<>();

        try {
            // 1. Загружаем страницу (Telegram Web Preview)
            Document doc = Jsoup.connect(source.getUrl()).get();

            // 2. Ищем все сообщения (div с классом tgme_widget_message)
            Elements messages = doc.select(".tgme_widget_message");

            if (messages.isEmpty()) {
                log.warn("Не нашел сообщений по ссылке: {}", source.getUrl());
                return Collections.emptyList();
            }

            // Безопасно получаем текущий ID (если null -> 0)
            int lastKnownId = (source.getLastPostId() == null) ? 0 : source.getLastPostId();
            int maxIdOnPage = lastKnownId;

            // 3. Проходим по сообщениям
            for (Element msg : messages) {
                // Извлекаем ID поста из атрибута data-post="durov/123"
                String dataPost = msg.attr("data-post"); // "channelname/123"
                if (dataPost.isEmpty()) continue;

                // Парсим ID поста
                int postId;
                try {
                    postId = Integer.parseInt(dataPost.split("/")[1]);
                } catch (Exception e) {
                    continue; // Пропускаем кривые посты
                }

                // Если пост НОВЕЕ, чем тот, что мы уже видели
                if (postId > lastKnownId) {

                    // Обновляем счетчик максимального ID, который мы видели на странице
                    if (postId > maxIdOnPage) {
                        maxIdOnPage = postId;
                    }

                    // Ищем текст внутри (класс tgme_widget_message_text)
                    Element textElement = msg.selectFirst(".tgme_widget_message_text");

                    if (textElement != null) {
                        // html() сохраняет ссылки, text() убирает все теги
                        String rawText = textElement.text();

                        // Если текст длинный и нормальный - берем
                        if (rawText.length() > 50) {
                            newPosts.add(rawText);
                        }
                    }
                }
            }

            // 4. Сохраняем новый lastPostId в базу, чтобы в следующий раз не брать эти посты
            if (maxIdOnPage > lastKnownId) {
                updateLastPostId(source.getId(), maxIdOnPage);
            }

        } catch (IOException e) {
            log.error("Ошибка парсинга {}: {}", source.getUrl(), e.getMessage());
        }

        return newPosts;
    }

    @Transactional
    public void updateLastPostId(Long sourceId, Integer newId) {
        sourceRepository.findById(sourceId).ifPresent(s -> {
            s.setLastPostId(newId);
            sourceRepository.save(s);
        });
    }
}
