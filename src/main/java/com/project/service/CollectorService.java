package com.project.service;

import com.project.entity.Channel;
import com.project.entity.PostQueue;
import com.project.entity.ProcessedNews;
import com.project.repository.ChannelRepository;
import com.project.repository.PostQueueRepository;
import com.project.repository.ProcessedNewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CollectorService {

    private final ChannelRepository channelRepository;
    private final ProcessedNewsRepository processedNewsRepository;
    private final PostQueueRepository postQueueRepository;
    private final OpenAIService openAIService; // Твой сервис с OpenAI

    // Запуск каждые 10 минут
    @Scheduled(fixedRate = 600000)
    public void collectNews() {
        log.info("Начинаю сбор новостей...");
        List<Channel> channels = channelRepository.findAll();

        if (channels.isEmpty()) {
            log.warn("Нет активных каналов в БД!");
            return;
        }

        for (Channel channel : channels) {
            try {
                processChannel(channel);
            } catch (Exception e) {
                log.error("Ошибка при обработке канала {}: {}", channel.getName(), e.getMessage());
            }
        }
        log.info("Сбор новостей завершен.");
    }

    private void processChannel(Channel channel) throws IOException {
        String username = channel.getUsername();
        if (username == null || username.isEmpty()) return;

        String url = "https://t.me/s/" + username;
        Document doc = Jsoup.connect(url).get();

        // Берем последний пост
        Elements posts = doc.select(".tgme_widget_message_text");
        if (posts.isEmpty()) return;

        String lastPostText = posts.last().text();

        // --- ЗАЩИТА ОТ ДУБЛЕЙ ---
        // Создаем уникальный ID новости (хэш текста + название канала)
        String uniqueId = String.valueOf((channel.getName() + lastPostText).hashCode());

        // Если новость уже была — пропускаем
        if (processedNewsRepository.existsById(uniqueId)) {
            return;
        }

        // Сохраняем, что мы видели эту новость
        ProcessedNews processedNews = new ProcessedNews();
        processedNews.setUrlHash(uniqueId);
        processedNews.setOriginalUrl(url);
        processedNewsRepository.save(processedNews);
        // -------------------------

        log.info("Генерирую пост для канала: {}", channel.getName());

        // Отправляем в OpenAI
        String summary = openAIService.summarize(lastPostText, channel.getSystemPrompt());

        // Сохраняем в очередь на отправку
        PostQueue postQueue = new PostQueue();
        postQueue.setChannel(channel);
        postQueue.setContent(summary);
        postQueue.setPriority(0);
        postQueue.setScheduledTime(LocalDateTime.now());
        postQueue.setStatus(PostQueue.Status.PENDING);

        postQueueRepository.save(postQueue);

        log.info("Пост добавлен в очередь!");
    }
}
