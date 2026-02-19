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
            Document doc = Jsoup.connect(source.getUrl())
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

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

                        // 1. Фото
                        Element photo = msg.selectFirst(".tgme_widget_message_photo_wrap");
                        if (photo != null) {
                            imageUrl = extractUrlFromStyle(photo.attr("style"));
                        }

                        // 2. Превью видео
                        if (imageUrl == null) {
                            Element videoThumb = msg.selectFirst(".tgme_widget_message_video_thumb");
                            if (videoThumb != null) {
                                imageUrl = extractUrlFromStyle(videoThumb.attr("style"));
                            }
                        }

                        // 3. Превью ссылки (link preview)
                        if (imageUrl == null) {
                            Element linkPreview = msg.selectFirst(".tgme_widget_message_link_preview_photo, .link_preview_image");
                            if (linkPreview != null) {
                                imageUrl = extractUrlFromStyle(linkPreview.attr("style"));
                                if (imageUrl == null) {
                                    Element imgTag = linkPreview.selectFirst("img");
                                    if (imgTag != null) {
                                        imageUrl = normalizeUrl(imgTag.attr("src"));
                                    }
                                }
                            }
                        }

                        // 4. Групповые фото (альбомы)
                        if (imageUrl == null) {
                            Element groupLayer = msg.selectFirst(".tgme_widget_message_grouped_layer");
                            if (groupLayer != null) {
                                imageUrl = extractUrlFromStyle(groupLayer.attr("style"));
                            }
                        }

                        if (textElement != null) {
                            String rawText = textElement.text();

                            if (rawText.length() > 50 && !isAd(rawText)) {
                                ParsedPost post = new ParsedPost();
                                post.setText(rawText);
                                post.setImageUrl(imageUrl);

                                newPosts.add(post);

                                if (imageUrl != null) {
                                    log.info("📸 Найдена картинка для поста {}", postId);
                                } else {
                                    log.warn("⚠️ ПОСТ БЕЗ КАРТИНКИ (ID {}). HTML:\n{}", postId, msg.outerHtml());
                                }
                            }
                        }
                    }
                }
            }

            if (maxIdOnPage > lastKnownId) {
                updateLastPostId(source.getId(), maxIdOnPage);
                if (isFirstRun) {
                    log.info("🏁 Первый запуск для {}. Пропускаем.", source.getName());
                }
            }

        } catch (IOException e) {
            log.error("Ошибка парсинга {}: {}", source.getUrl(), e.getMessage());
        }

        return newPosts;
    }

    private String extractUrlFromStyle(String style) {
        Pattern pattern = Pattern.compile("url\\('?(.*?)'?\\)");
        Matcher matcher = pattern.matcher(style);
        if (matcher.find()) {
            String url = matcher.group(1);
            return normalizeUrl(url);
        }
        return null;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        // на всякий случай
        return "https://" + url;
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
