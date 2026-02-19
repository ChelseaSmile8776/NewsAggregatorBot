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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParserService {

    private final SourceRepository sourceRepository;

    public static class ParsedPost {
        private int postId;
        private String text;
        private String imageUrl;

        public int getPostId() { return postId; }
        public void setPostId(int postId) { this.postId = postId; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }

    @Transactional
    public List<ParsedPost> parseNewPosts(Source sourceArg) {
        List<ParsedPost> newPosts = new ArrayList<>();

        // КЛЮЧ: берём Source заново из БД, иначе можно постоянно видеть старый lastPostId и крутиться по кругу
        Source source = sourceRepository.findById(sourceArg.getId())
                .orElseThrow(() -> new IllegalStateException("Source not found: " + sourceArg.getId()));

        try {
            Document doc = Jsoup.connect(source.getUrl())
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(15000)
                    .get();

            Elements messages = doc.select("div.tgme_widget_message[data-post]");
            if (messages.isEmpty()) {
                log.warn("Не нашел сообщений по ссылке: {}", source.getUrl());
                return Collections.emptyList();
            }

            int lastKnownId = (source.getLastPostId() == null) ? 0 : source.getLastPostId();
            boolean isFirstRun = (lastKnownId == 0);

            int maxSeenOnPage = lastKnownId;

            // анти-дубликат, если в DOM почему-то повторяются блоки
            Set<Integer> seenIdsOnPage = new HashSet<>();

            for (Element msg : messages) {
                String dataPost = msg.attr("data-post"); // "DeCenter/24049"
                if (dataPost == null || dataPost.isBlank() || !dataPost.contains("/")) continue;

                int postId;
                try {
                    postId = Integer.parseInt(dataPost.substring(dataPost.lastIndexOf('/') + 1));
                } catch (Exception e) {
                    continue;
                }

                if (postId > maxSeenOnPage) maxSeenOnPage = postId;

                if (!seenIdsOnPage.add(postId)) {
                    continue;
                }

                // уже обработано
                if (postId <= lastKnownId) continue;

                // первый запуск: ничего не возвращаем, но watermark продвинем ниже
                if (isFirstRun) continue;

                Element textElement = msg.selectFirst(".tgme_widget_message_text");
                if (textElement == null) continue;

                String rawText = textElement.text();
                if (rawText == null) continue;

                if (rawText.length() <= 50) continue;
                if (isAd(rawText)) continue;

                String imageUrl = null;

                // 1) Фото
                Element photo = msg.selectFirst(".tgme_widget_message_photo_wrap");
                if (photo != null) {
                    imageUrl = extractUrlFromStyle(photo.attr("style"));
                }

                // 2) Превью видео (пока как у тебя)
                if (imageUrl == null) {
                    Element videoThumb = msg.selectFirst(".tgme_widget_message_video_thumb");
                    if (videoThumb != null) {
                        imageUrl = extractUrlFromStyle(videoThumb.attr("style"));
                    }
                }

                // 3) Превью ссылки
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

                // 4) Групповые фото (альбомы)
                if (imageUrl == null) {
                    Element groupLayer = msg.selectFirst(".tgme_widget_message_grouped_layer");
                    if (groupLayer != null) {
                        imageUrl = extractUrlFromStyle(groupLayer.attr("style"));
                    }
                }

                ParsedPost post = new ParsedPost();
                post.setPostId(postId);
                post.setText(rawText);
                post.setImageUrl(imageUrl);
                newPosts.add(post);

                if (imageUrl != null) {
                    log.info("📸 Найдена картинка для поста {}", postId);
                } else {
                    log.warn("⚠️ ПОСТ БЕЗ КАРТИНКИ (ID {}). HTML:\n{}", postId, msg.outerHtml());
                }
            }

            // порядок: старые -> новые
            newPosts.sort(Comparator.comparingInt(ParsedPost::getPostId));

            // КЛЮЧ: двигаем lastPostId всегда (иначе на следующем цикле будут те же "новые" снова)
            if (maxSeenOnPage > lastKnownId) {
                source.setLastPostId(maxSeenOnPage);
                sourceRepository.save(source);

                if (isFirstRun) {
                    log.info("🏁 Первый запуск для {}. Пропускаем старые посты, last_post_id={}",
                            source.getName(), maxSeenOnPage);
                } else {
                    log.info("🧷 Обновили last_post_id для {}: {} -> {}",
                            source.getName(), lastKnownId, maxSeenOnPage);
                }
            }

        } catch (IOException e) {
            log.error("Ошибка парсинга {}: {}", source.getUrl(), e.getMessage(), e);
        }

        return newPosts;
    }

    private String extractUrlFromStyle(String style) {
        if (style == null) return null;
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
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
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
}
