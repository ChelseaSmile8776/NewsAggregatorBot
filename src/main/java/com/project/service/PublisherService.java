package com.project.service;

import com.project.bot.NewsBot;
import com.project.entity.PostQueue;
import com.project.repository.PostQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    public void publishPosts() {
        // Берем посты со статусом PENDING (ожидают отправки) и временем <= сейчас
        List<PostQueue> posts = postQueueRepository.findAllByStatusAndScheduledTimeBefore(
                PostQueue.Status.PENDING, LocalDateTime.now()
        );

        if (posts.isEmpty()) return;

        log.info("Нашел {} постов для публикации", posts.size());

        for (PostQueue post : posts) {
            try {
                // Отправляем тебе (ID пока жестко задан, но можно брать из базы User)
                newsBot.sendText(508490900L, post.getContent());

                // Меняем статус на PUBLISHED
                post.setStatus(PostQueue.Status.PUBLISHED);
                postQueueRepository.save(post);

                log.info("Пост отправлен!");

            } catch (Exception e) {
                log.error("Ошибка публикации: {}", e.getMessage());
                post.setStatus(PostQueue.Status.ERROR);
                postQueueRepository.save(post);
            }
        }
    }
}
