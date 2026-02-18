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
            // 1. Загружаем страницу
            Document doc = Jsoup.connect(source.getUrl()).get();
            Elements messages = doc.select(".tgme_widget_message");

            if (messages.isEmpty()) {
                log.warn("Не нашел сообщений по ссылке: {}", source.getUrl());
                return Collections.emptyList();
            }

            // Определяем текущий последний ID (если null -> 0)
            int lastKnownId = (source.getLastPostId() == null) ? 0 : source.getLastPostId();
            int maxIdOnPage = lastKnownId;

            // 🔥 ФЛАГ: Если это первый запуск (ID=0), то мы НЕ публикуем старые посты
            boolean isFirstRun = (lastKnownId == 0);

            // 2. Проходим по сообщениям
            for (Element msg : messages) {
                String dataPost = msg.attr("data-post");
                if (dataPost.isEmpty()) continue;

                int postId;
                try {
                    postId = Integer.parseInt(dataPost.split("/")[1]);
                } catch (Exception e) {
                    continue;
                }

                // Если пост новее того, что мы знаем
                if (postId > lastKnownId) {

                    // Обновляем счетчик максимального ID на странице
                    if (postId > maxIdOnPage) {
                        maxIdOnPage = postId;
                    }

                    // 🔥 Если это НЕ первый запуск — собираем посты для публикации
                    if (!isFirstRun) {
                        Element textElement = msg.selectFirst(".tgme_widget_message_text");
                        if (textElement != null) {
                            String rawText = textElement.text();
                            if (rawText.length() > 50) {
                                newPosts.add(rawText);
                            }
                        }
                    }
                }
            }

            // 3. Сохраняем новый lastPostId в базу
            if (maxIdOnPage > lastKnownId) {
                updateLastPostId(source.getId(), maxIdOnPage);

                if (isFirstRun) {
                    log.info("🏁 Первый запуск для {}. Пропускаем публикацию, запомнили ID: {}", source.getName(), maxIdOnPage);
                }
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
