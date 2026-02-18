package com.project.service;

import com.project.bot.NewsBot;
import com.project.entity.PostQueue;
import com.project.repository.PostQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublisherService {

    private final PostQueueRepository queueRepository;
    private final NewsBot bot;

    // Проверяет очередь каждую минуту
    @Scheduled(fixedRate = 60000)
    public void publishScheduled() {
        LocalDateTime now = LocalDateTime.now();

        // Берем топ-5 готовых постов
        List<PostQueue> posts = queueRepository.findReadyToPublish(now, PageRequest.of(0, 5));

        for (PostQueue post : posts) {
            log.info("Публикую пост ID: {} в канал {}", post.getId(), post.getChannel().getName());

            try {
                // Добавляем подпись, если есть
                String finalContent = post.getContent();
                if (post.getChannel().getSignature() != null) {
                    finalContent += "\n\n" + post.getChannel().getSignature();
                }

                bot.sendText(Long.parseLong(post.getChannel().getChannelId()), finalContent);

                post.setStatus(PostQueue.Status.PUBLISHED);
            } catch (Exception e) {
                log.error("Ошибка публикации: {}", e.getMessage());
                post.setStatus(PostQueue.Status.ERROR);
            }
            queueRepository.save(post);
        }
    }
}
