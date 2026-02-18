package com.project.service;

import com.project.bot.NewsBot;
import com.project.entity.PostQueue;
import com.project.repository.PostQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // <-- Не забываем транзакции

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublisherService {

    private final PostQueueRepository postQueueRepository;
    private final NewsBot newsBot;

    // Проверяем очередь каждую минуту
    @Scheduled(fixedRate = 60000)
    @Transactional // <-- ВАЖНО: Держим сессию для подгрузки TargetChannel
    public void publishPosts() {
        // Берем посты со статусом PENDING и временем <= сейчас
        List<PostQueue> posts = postQueueRepository.findAllByStatusAndScheduledTimeBefore(
                PostQueue.Status.PENDING, LocalDateTime.now()
        );

        if (posts.isEmpty()) return;

        log.info("Нашел {} постов для публикации", posts.size());

        for (PostQueue post : posts) {
            try {
                // <-- ИЗМЕНЕНИЕ: Берем ID канала из объекта TargetChannel
                String targetChatId = post.getTargetChannel().getTelegramId();
                String targetTitle = post.getTargetChannel().getTitle();

                log.info("Отправляю пост в канал '{}' (ID: {})", targetTitle, targetChatId);

                // Отправляем в НУЖНЫЙ канал
                newsBot.sendText(Long.parseLong(targetChatId), post.getContent());

                // Меняем статус на SENT
                post.setStatus(PostQueue.Status.SENT);
                postQueueRepository.save(post);

                log.info("Пост успешно отправлен в канал '{}'!", targetTitle);

            } catch (Exception e) {
                log.error("Ошибка публикации поста id={} в канал {}: {}", post.getId(), post.getTargetChannel().getTitle(), e.getMessage());
                post.setStatus(PostQueue.Status.ERROR);
                postQueueRepository.save(post);
            }
        }
    }
}
