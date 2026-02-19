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
        private boolean isVideo; // <--- NEW

        public int getPostId() { return postId; }
        public void setPostId(int postId) { this.postId = postId; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public boolean isVideo() { return isVideo; }
        public void setVideo(boolean video) { isVideo = video; }
    }

    @Transactional
    public List<ParsedPost> parseNewPosts(Source sourceArg) {
        List<ParsedPost> newPosts = new ArrayList<>();

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
            Set<Integer> seenIdsOnPage = new HashSet<>();

            for (Element msg : messages) {
                String dataPost = msg.attr("data-post");
                if (dataPost == null || dataPost.isBlank() || !dataPost.contains("/")) continue;

                int postId;
                try {
                    postId = Integer.parseInt(dataPost.substring(dataPost.lastIndexOf('/') + 1));
                } catch (Exception e) {
                    continue;
                }

                if (postId > maxSeenOnPage) maxSeenOnPage = postId;
                if (!seenIdsOnPage.add(postId)) continue;
                if (postId <= lastKnownId) continue;
                if (isFirstRun) continue;

                Element textElement = msg.selectFirst(".tgme_widget_message_text");
                if (textElement == null) continue;

                String rawText = textElement.text();
                if (rawText == null || rawText.length() <= 50 || isAd(rawText)) continue;

                String mediaUrl = null;
                boolean isVideo = false;

                // 1. Пробуем найти видео (тег <video>)
                Element videoTag = msg.selectFirst("video");
                if (videoTag != null) {
                    if (videoTag.hasAttr("src")) {
                        mediaUrl = normalizeUrl(videoTag.attr("src"));
                        isVideo = true;
                    }
                }

                // 2. Если нет видео-тега, ищем превью видео (класс video_thumb)
                // (Тут мы не знаем URL самого видео, поэтому берем картинку, но помечаем, что это "было видео",
                //  хотя без прямой ссылки на mp4 бот всё равно отправит как фото, т.к. sendVideo требует видео-файл).
                //  ВАЖНО: Телеграм-виджет часто не отдает прямой URL видео. Если его нет — шлём превью как фото.
                if (mediaUrl == null) {
                    Element videoThumb = msg.selectFirst(".tgme_widget_message_video_thumb");
                    if (videoThumb != null) {
                        mediaUrl = extractUrlFromStyle(videoThumb.attr("style"));
                        // isVideo = true; // <-- Если раскомментить, бот попробует отправить картинку методом sendVideo и упадет.
                        // Поэтому оставляем false, чтобы ушло как фото.
                    }
                }

                // 3. Если нет видео, ищем просто фото
                if (mediaUrl == null) {
                    Element photo = msg.selectFirst(".tgme_widget_message_photo_wrap");
                    if (photo != null) {
                        mediaUrl = extractUrlFromStyle(photo.attr("style"));
                    }
                }

                // 4. Link preview
                if (mediaUrl == null) {
                    Element linkPreview = msg.selectFirst(".tgme_widget_message_link_preview_photo, .link_preview_image");
                    if (linkPreview != null) {
                        mediaUrl = extractUrlFromStyle(linkPreview.attr("style"));
                        if (mediaUrl == null) {
                            Element imgTag = linkPreview.selectFirst("img");
                            if (imgTag != null) {
                                mediaUrl = normalizeUrl(imgTag.attr("src"));
                            }
                        }
                    }
                }

                ParsedPost post = new ParsedPost();
                post.setPostId(postId);
                post.setText(rawText);
                post.setImageUrl(mediaUrl);
                post.setVideo(isVideo); // <---

                newPosts.add(post);

                if (mediaUrl != null) {
                    log.info("📸/🎥 Медиа ({}) для поста {}", isVideo ? "VIDEO" : "PHOTO", postId);
                }
            }

            newPosts.sort(Comparator.comparingInt(ParsedPost::getPostId));

            if (maxSeenOnPage > lastKnownId) {
                source.setLastPostId(maxSeenOnPage);
                sourceRepository.save(source);
                if (!isFirstRun) {
                    log.info("🧷 Обновили last_post_id для {}: {} -> {}", source.getName(), lastKnownId, maxSeenOnPage);
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
            return normalizeUrl(matcher.group(1));
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
        if (text == null) return false;

        String lower = text.toLowerCase().trim();

        // 🔥 РЕКЛАМА/БУКМЕКЕРЫ/СПАМ
        String[] adKeywords = {
                "подписывайтесь", "читать далее", "erid:", "реклама", "ставки", "казино",
                "melbet", "1xbet", "фонабет", "париматч", "винлайн", "бонус", "промокод",
                "заработай", "заработок", "присоединяйся", "покупай", "закажи", "забрать",
                "бонус", "бонусы", "компания", "запускает"
        };

        // ⚠️ РИСКИ (политика/война)
        String[] riskKeywords = {
                "путин", "медведев", "патрушев", "шойгу", "лавров",
                "набиуллина", "мишустин", "силуанов", "зеленский", "украина", "мобилизация",
                "спецоперация", "денацификация", "террористы", "нацисты", "всу", "азов",
                "крым", "донбасс", "лднр", "днр", "лнр"
        };

        // Проверяем все ключевые слова
        for (String keyword : adKeywords) {
            if (lower.contains(keyword)) return true;
        }
        for (String keyword : riskKeywords) {
            if (lower.contains(keyword)) return true;
        }

        // 📊 >10 слешей = спам со ссылками
        if (lower.chars().filter(ch -> ch == '/').count() > 10) return true;

        // 📢 >5 !? = кричащий спам
        if (lower.chars().filter(ch -> ch == '!' || ch == '?').count() > 5) return true;

        return false;
    }


}
