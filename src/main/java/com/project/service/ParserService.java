package com.project.service;

import com.project.entity.Source;
import com.project.repository.SourceRepository;
import lombok.Data;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParserService {

    private final SourceRepository sourceRepository;

    @Data
    public static class ParsedPost {
        private String text;
        private String imageUrl;
    }

    public List<ParsedPost> parseNewPosts(Source source) {
        List<ParsedPost> newPosts = new ArrayList<>();

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
                    if (postId > maxIdOnPage) maxIdOnPage = postId;

                    if (!isFirstRun) {
                        Element textElement = msg.selectFirst(".tgme_widget_message_text");
                        String imageUrl = null;

                        // 1. Обычное фото (single photo)
                        Element photoElement = msg.selectFirst(".tgme_widget_message_photo_wrap");
                        if (photoElement != null) {
                            imageUrl = extractUrlFromStyle(photoElement.attr("style"));
                        }

                        // 2. Если нет фото, ищем ВИДЕО (берем превью)
                        if (imageUrl == null) {
                            Element videoElement = msg.selectFirst("video");
                            if (videoElement != null) {
                                // У видео часто есть атрибут poster="url"
                                imageUrl = videoElement.attr("poster");
                            }
                        }

                        // 3. Если нет, ищем ГРУППУ фото (берем первую)
                        if (imageUrl == null) {
                            Element groupPhoto = msg.selectFirst(".tgme_widget_message_grouped_layer");
                            if (groupPhoto != null) {
                                imageUrl = extractUrlFromStyle(groupPhoto.attr("style"));
                            }
                        }

                        if (textElement != null) {
                            String rawText = textElement.text();

                            // Фильтр
                            if (rawText.length() > 50 && !isAd(rawText)) {
                                ParsedPost post = new ParsedPost();
                                post.setText(rawText);
                                post.setImageUrl(imageUrl); // Теперь тут может быть превью видео
                                newPosts.add(post);
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

    private String extractUrlFromStyle(String style) {
        // Правильная регулярка с экранированием для Java
        Pattern pattern = Pattern.compile("url\\('?(.*?)'?\\)");
        Matcher matcher = pattern.matcher(style);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean isAd(String text) {
        String lower = text.toLowerCase();
        return lower.contains("подписывайтесь") ||
                lower.contains("читать далее") ||
                lower.contains("erid:") ||
                lower.contains("реклама") ||
                lower.contains("ставки") ||
                lower.contains("казино") ||
                lower.contains("melbet") ||
                lower.contains("1xbet");
    }

    @Transactional
    public void updateLastPostId(Long sourceId, Integer newId) {
        sourceRepository.findById(sourceId).ifPresent(s -> {
            s.setLastPostId(newId);
            sourceRepository.save(s);
        });
    }
}
