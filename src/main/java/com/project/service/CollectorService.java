package com.project.service;

import com.project.entity.Channel;
import com.project.entity.PostQueue;
import com.project.repository.ChannelRepository;
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
public class CollectorService {

    private final ChannelRepository channelRepository;
    private final PostQueueRepository postQueueRepository;
    private final OpenAIService openAIService;

    // Запускается раз в 10 минут (600000 мс)
    @Scheduled(fixedRate = 600000)
    public void collectNews() {
        log.info("Начинаю сбор новостей...");

        List<Channel> channels = channelRepository.findAll();
        if (channels.isEmpty()) {
            log.warn("Нет активных каналов в БД!");
            return;
        }

        for (Channel channel : channels) {
            // ТУТ БУДЕТ ПАРСИНГ RSS
            // Пока имитируем новость
            String fakeNews = "Apple выпустила новый iPhone 16. Он имеет титановый корпус и кнопку Action Button.";

            log.info("Генерирую пост для канала: {}", channel.getName());

            String aiPost = openAIService.generateSummary(fakeNews, channel.getSystemPrompt());

            if (aiPost != null) {
                PostQueue post = new PostQueue();
                post.setChannel(channel);
                post.setContent(aiPost);
                post.setStatus(PostQueue.Status.PENDING);
                post.setScheduledTime(LocalDateTime.now()); // Публиковать сразу (для теста)
                post.setPriority(0);

                postQueueRepository.save(post);
                log.info("Пост добавлен в очередь!");
            }
        }
    }
}
