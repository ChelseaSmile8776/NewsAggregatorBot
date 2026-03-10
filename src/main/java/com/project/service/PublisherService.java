package com.project.service;

import com.project.bot.NewsBot;
import com.project.entity.PostQueue;
import com.project.repository.PostQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublisherService {

    private static final int STALE_HOURS = 4;

    private final PostQueueRepository postQueueRepository;
    private final NewsBot newsBot;

    @Scheduled(fixedDelay = 600000)
    @Transactional
    public void publishNextPost() {
        log.info("🚀 === PUBLISHER ЗАПУЩЕН! {} ===", LocalDateTime.now());

        // 🎥 1. ПРОВЕРЯЕМ ВИДЕО ПЕРВЫМИ (mp4)
        List<PostQueue> videoPosts = postQueueRepository.findByStatusOrderByScheduledTimeAsc(PostQueue.Status.PENDING)
                .stream()
                .filter(p -> p.getImageUrl() != null && p.getImageUrl().toLowerCase().contains(".mp4"))
                .limit(1)
                .collect(Collectors.toList());

        if (!videoPosts.isEmpty()) {
            PostQueue videoPost = videoPosts.get(0);
            log.info("🎥 ВИДЕО ПРИОРИТЕТ! ID={} канал={} {}",
                    videoPost.getId(), videoPost.getTargetChannel().getTitle(), videoPost.getImageUrl());
            publishPost(videoPost);
            return;
        }

        // 📸 2. Обычная очередь
        List<PostQueue> queue = postQueueRepository.findByStatusOrderByScheduledTimeAsc(PostQueue.Status.PENDING);
        log.info("📊 В очереди PENDING постов: {}", queue.size());

        if (queue.isEmpty()) {
            log.info("📭 Очередь пуста, отдыхаем.");
            return;
        }

        PostQueue post = queue.get(0);
        log.info("📤 Публикую пост ID={} в канал {}", post.getId(), post.getTargetChannel().getTitle());
        publishPost(post);
    }

    private void publishPost(PostQueue post) {
        // 🆕 Пропускаем залежавшиеся посты (накопились во время даунтайма)
        if (post.getScheduledTime() != null &&
                post.getScheduledTime().isBefore(LocalDateTime.now().minusHours(STALE_HOURS))) {
            log.warn("🗑️ Пост ID={} устарел (старше {}ч), пропускаю", post.getId(), STALE_HOURS);
            post.setStatus(PostQueue.Status.ERROR); // или добавь статус EXPIRED если хочешь отличать
            postQueueRepository.save(post);
            return;
        }

        try {
            Long chatId = Long.parseLong(post.getTargetChannel().getTelegramId());
            String cleanContent = cleanHtml(post.getContent());
            String url = post.getImageUrl();

            if (url != null && !url.trim().isEmpty()) {
                if (isVideoUrl(url)) {
                    log.info("🎥 ВИДЕО ОТПРАВЛЯЮ: {}", url);
                    newsBot.sendVideo(chatId, url, cleanContent);
                } else {
                    log.info("🖼️ ФОТО ОТПРАВЛЯЮ: {}", url);
                    newsBot.sendPhoto(chatId, url, cleanContent);
                }
            } else {
                log.info("📝 ТЕКСТ ОТПРАВЛЯЮ");
                newsBot.sendText(chatId, cleanContent);
            }

            post.setStatus(PostQueue.Status.SENT);
            postQueueRepository.save(post);
            log.info("✅ Пост ID={} ОТПРАВЛЕН", post.getId());

        } catch (Exception e) {
            log.error("❌ ОШИБКА ID={}: {}", post.getId(), e.getMessage(), e);
            post.setStatus(PostQueue.Status.ERROR);
            postQueueRepository.save(post);
        }
    }

    private boolean isVideoUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains(".mp4") ||
                lower.contains(".mov") ||
                lower.contains(".avi") ||
                lower.contains(".mkv") ||
                lower.contains("blob:") ||
                lower.contains("video/");
    }

    private String cleanHtml(String input) {
        if (input == null) return "";
        return input
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("<p>", "")
                .replace("</p>", "\n\n")
                .replace("**", "")
                .trim();
    }
}
