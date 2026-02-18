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
        // Ищем посты со статусом PENDING
        List<PostQueue> queue = postQueueRepository.findByStatusOrderByScheduledTimeAsc(PostQueue.Status.PENDING);

        if (queue.isEmpty()) {
            log.info("📭 Очередь пуста, отдыхаем.");
            return;
        }

        // Берем самый старый
        PostQueue post = queue.get(0);

        log.info("📤 Публикую пост ID={} в канал {}", post.getId(), post.getTargetChannel().getTitle());

        try {
            Long chatId = Long.parseLong(post.getTargetChannel().getTelegramId());

            if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
                newsBot.sendPhoto(chatId, post.getImageUrl(), post.getContent());
            } else {
                newsBot.sendText(chatId, post.getContent());
            }

            post.setStatus(PostQueue.Status.SENT);
            postQueueRepository.save(post);

        } catch (Exception e) {
            log.error("❌ Ошибка публикации: {}", e.getMessage());
            post.setStatus(PostQueue.Status.ERROR);
            postQueueRepository.save(post);
        }
    }
}
