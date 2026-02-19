package com.project.service;

import com.project.bot.NewsBot;
import com.project.entity.PostQueue;
import com.project.repository.PostQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublisherService {

    private final PostQueueRepository postQueueRepository;
    private final NewsBot newsBot;

    // Публикация раз в 5 минут
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void publishNextPost() {
        // Берем старые "зависшие" PENDING посты, если нужно, или просто FIFO
        List<PostQueue> queue = postQueueRepository.findByStatusOrderByScheduledTimeAsc(PostQueue.Status.PENDING);

        if (queue.isEmpty()) {
            log.info("📭 Очередь пуста, отдыхаем.");
            return;
        }

        PostQueue post = queue.get(0);
        log.info("📤 Публикую пост ID={} в канал {}", post.getId(), post.getTargetChannel().getTitle());

        try {
            Long chatId = Long.parseLong(post.getTargetChannel().getTelegramId());
            String cleanContent = cleanHtml(post.getContent());
            String url = post.getImageUrl(); // может быть null

            if (url != null && !url.trim().isEmpty()) {
                // Пытаемся понять, это видео или фото
                if (isVideoUrl(url)) {
                    log.info("🎥 Обнаружена ссылка на видео: {}", url);
                    newsBot.sendVideo(chatId, url, cleanContent);
                } else {
                    log.info("🖼️ Отправляю как фото: {}", url);
                    newsBot.sendPhoto(chatId, url, cleanContent);
                }
            } else {
                log.info("📝 Только текст (нет media url)");
                newsBot.sendText(chatId, cleanContent);
            }

            post.setStatus(PostQueue.Status.SENT);
            postQueueRepository.save(post);
            log.info("✅ Пост ID={} опубликован", post.getId());

        } catch (Exception e) {
            log.error("❌ Ошибка публикации ID={}: {}", post.getId(), e.getMessage(), e);
            // Можно поставить ERROR, чтобы не блокировать очередь навечно этим постом
            post.setStatus(PostQueue.Status.ERROR);
            postQueueRepository.save(post);
        }
    }

    private boolean isVideoUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.contains("blob:");
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
