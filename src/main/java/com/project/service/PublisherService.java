package com.project.service;

import com.project.bot.NewsBot;
import com.project.entity.PostQueue;
import com.project.repository.PostQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublisherService {

    private final PostQueueRepository postQueueRepository;
    private final NewsBot newsBot;

    // Запускаем каждую минуту
    @Scheduled(fixedRate = 60000)
    public void publishPosts() {
        // 1. Находим посты (БЕЗ транзакции, просто SELECT)
        // Но чтобы подгрузить TargetChannel, нам, возможно, понадобится транзакция при чтении.
        // Поэтому лучше сделать так:
        List<PostQueue> posts = findPendingPosts();

        if (posts.isEmpty()) return;

        log.info("Нашел {} постов для публикации", posts.size());

        for (PostQueue post : posts) {
            try {
                processSinglePost(post); // 2. Обрабатываем каждый пост отдельно
            } catch (Exception e) {
                log.error("Критическая ошибка при обработке поста {}: {}", post.getId(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<PostQueue> findPendingPosts() {
        return postQueueRepository.findAllByStatusAndScheduledTimeBefore(
                PostQueue.Status.PENDING, LocalDateTime.now()
        );
    }

    // Этот метод выполняется в СВОЕЙ транзакции. Даже если упадет, остальные посты не пострадают.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSinglePost(PostQueue post) {
        try {
            // Подгружаем данные (они уже должны быть в кэше или подгрузятся в транзакции)
            String targetChatId = post.getTargetChannel().getTelegramId();
            String targetTitle = post.getTargetChannel().getTitle();

            log.info("Отправляю пост в канал '{}' (ID: {})", targetTitle, targetChatId);

            newsBot.sendText(Long.parseLong(targetChatId), post.getContent());

            post.setStatus(PostQueue.Status.SENT);
            postQueueRepository.saveAndFlush(post); // Сохраняем немедленно!

            log.info("Пост успешно отправлен в канал '{}'!", targetTitle);

        } catch (Exception e) {
            log.error("Ошибка публикации поста id={}: {}", post.getId(), e.getMessage());
            post.setStatus(PostQueue.Status.ERROR);
            postQueueRepository.saveAndFlush(post); // Сохраняем ошибку немедленно
        }
    }
}
