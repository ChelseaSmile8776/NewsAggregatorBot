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
            Document doc = Jsoup.connect(source.getUrl()).get();
            Elements messages = doc.select(".tgme_widget_message");

            if (messages.isEmpty()) {
                log.warn("Не нашел сообщений по ссылке: {}", source.getUrl());
                return Collections.emptyList();
            }

            int lastKnownId = (source.getLastPostId() == null) ? 0 : source.getLastPostId();
            int maxIdOnPage = lastKnownId;
            boolean isFirstRun = (lastKnownId == 0);

            for (Element msg : messages) {
                String dataPost = msg.attr("data-post");
                if (dataPost.isEmpty()) continue;

                int postId;
                try {
                    postId = Integer.parseInt(dataPost.split("/")[1]);
                } catch (Exception e) {
                    continue;
                }

                if (postId > lastKnownId) {
                    if (postId > maxIdOnPage) {
                        maxIdOnPage = postId;
                    }

                    if (!isFirstRun) {
                        Element textElement = msg.selectFirst(".tgme_widget_message_text");
                        if (textElement != null) {
                            String rawText = textElement.text();

                            // Фильтр по длине и стоп-словам
                            if (rawText.length() > 50 && !isAd(rawText)) {
                                newPosts.add(rawText);
                            }
                        }
                    }
                }
            }

            if (maxIdOnPage > lastKnownId) {
                updateLastPostId(source.getId(), maxIdOnPage);
                if (isFirstRun) {
                    log.info("🏁 Первый запуск для {}. Пропускаем публикацию.", source.getName());
                }
            }

        } catch (IOException e) {
            log.error("Ошибка парсинга {}: {}", source.getUrl(), e.getMessage());
        }

        return newPosts;
    }

    // Простейший фильтр рекламы
    private boolean isAd(String text) {
        String lower = text.toLowerCase();
        return lower.contains("подписывайтесь") ||
                lower.contains("читать далее") ||
                lower.contains("erid:") ||
                lower.contains("реклама") ||
                lower.contains("ставки") ||
                lower.contains("казино") ||
                lower.contains("melbet") ||
                lower.contains("1xbet") ||
                lower.contains("выигрыш");
    }

    @Transactional
    public void updateLastPostId(Long sourceId, Integer newId) {
        sourceRepository.findById(sourceId).ifPresent(s -> {
            s.setLastPostId(newId);
            sourceRepository.save(s);
        });
    }
}
