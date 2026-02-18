package com.project.service;

import com.project.entity.Source;
import com.project.entity.PostQueue;
import com.project.entity.ProcessedNews;
import com.project.entity.TargetChannel;
import com.project.repository.SourceRepository;
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

    private final SourceRepository sourceRepository; // <-- Был ChannelRepository
    private final ProcessedNewsRepository processedNewsRepository;
    private final PostQueueRepository postQueueRepository;
    private final OpenAIService openAIService;

    @Scheduled(fixedRate = 600000) // 10 минут
    public void collectNews() {
        log.info("Начинаю сбор новостей...");
        List<Source> sources = sourceRepository.findAll(); // <-- Берем источники

        if (sources.isEmpty()) {
            log.warn("Нет активных источников!");
            return;
        }

        for (Source source : sources) {
            try {
                processSource(source);
            } catch (Exception e) {
                log.error("Ошибка при обработке источника {}: {}", source.getName(), e.getMessage());
            }
        }
    }

    private void processSource(Source source) throws IOException {
        String url = source.getUrl();
        if (url == null || url.isEmpty()) return;

        // Если у источника нет целевого канала (куда постить) — пропускаем
        TargetChannel targetChannel = source.getTargetChannel();
        if (targetChannel == null) {
            log.warn("Источник {} не привязан ни к одному каналу! Пропускаем.", source.getName());
            return;
        }

        Document doc = Jsoup.connect(url).get();
        Elements posts = doc.select(".tgme_widget_message_text");

        if (posts.isEmpty()) return;

        String lastPostText = posts.last().text();

        // Проверка на пустоту
        if (lastPostText == null || lastPostText.trim().isEmpty()) {
            return;
        }

        // Уникальный ID новости
        String uniqueId = String.valueOf((source.getName() + lastPostText).hashCode());

        if (processedNewsRepository.existsById(uniqueId)) {
            return;
        }

        // Сохраняем новость как обработанную
        ProcessedNews processedNews = new ProcessedNews();
        processedNews.setUrlHash(uniqueId);
        processedNews.setOriginalUrl(url);
        processedNewsRepository.save(processedNews);

        log.info("Генерирую пост из источника: {}", source.getName());

        String summary = openAIService.summarize(lastPostText, source.getSystemPrompt());

        // Сохраняем в очередь ДЛЯ НУЖНОГО КАНАЛА
        PostQueue postQueue = new PostQueue();
        postQueue.setTargetChannel(targetChannel); // <-- ВАЖНО: Привязываем к TargetChannel
        postQueue.setContent(summary);
        postQueue.setPriority(0);
        postQueue.setScheduledTime(LocalDateTime.now());
        postQueue.setStatus(PostQueue.Status.PENDING);

        postQueueRepository.save(postQueue);

        log.info("Пост добавлен в очередь для канала {}!", targetChannel.getTitle());
    }
}
