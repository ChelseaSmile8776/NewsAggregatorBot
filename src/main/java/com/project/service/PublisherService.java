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

    // Публикация раз в 5 минут (300000 мс)
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void publishNextPost() {
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

            if (post.getImageUrl() != null && !post.getImageUrl().trim().isEmpty()) {
                log.info("🖼️ Пробую фото: {}", post.getImageUrl());
                newsBot.sendPhoto(chatId, post.getImageUrl(), cleanContent);
            } else {
                log.info("📝 Только текст (нет image_url)");
                newsBot.sendText(chatId, cleanContent);
            }

            post.setStatus(PostQueue.Status.SENT);
            postQueueRepository.save(post);
            log.info("✅ Пост ID={} опубликован", post.getId());

        } catch (Exception e) {
            log.error("❌ Ошибка публикации ID={}: {}", post.getId(), e.getMessage(), e);
            post.setStatus(PostQueue.Status.ERROR);
            postQueueRepository.save(post);
        }
    }

    // Метод для очистки HTML под стандарты Telegram
    private String cleanHtml(String input) {
        if (input == null) return "";
        return input
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("<p>", "")
                .replace("</p>", "\n\n")
                .replace("**", "") // Иногда GPT путает MD и HTML
                .trim();
    }
}
