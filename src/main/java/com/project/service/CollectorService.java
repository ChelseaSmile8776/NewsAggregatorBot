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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CollectorService {

    private final SourceRepository sourceRepository;
    private final ProcessedNewsRepository processedNewsRepository;
    private final PostQueueRepository postQueueRepository;
    private final OpenAIService openAIService;

    //@Scheduled(fixedRate = 600000) // 10 минут
    @Transactional
    public void collectNews() {
        log.info("Начинаю сбор новостей...");
        List<Source> sources = sourceRepository.findAll();

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
        log.info("Сбор новостей завершен.");
    }

    private void processSource(Source source) throws IOException {
        log.info("Обрабатываю источник: {}", source.getName());

        String url = source.getUrl();
        if (url == null || url.isEmpty()) {
            log.warn("У источника {} нет URL!", source.getName());
            return;
        }

        if (!url.contains("/s/")) {
            url = url.replace("t.me/", "t.me/s/");
        }

        TargetChannel targetChannel = source.getTargetChannel();
        if (targetChannel == null) {
            log.warn("Источник {} не привязан ни к одному каналу! Пропускаем.", source.getName());
            return;
        }

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .get();
        Elements posts = doc.select(".tgme_widget_message_text");

        if (posts.isEmpty()) {
            log.warn("Не нашел постов на странице {}!", url);
            return;
        }

        String lastPostText = posts.last().text();

        if (lastPostText == null || lastPostText.trim().isEmpty()) {
            log.info("Последний пост пустой (или картинка без текста). Пропускаем.");
            return;
        }

        String uniqueId = String.valueOf((source.getName() + lastPostText).hashCode());

        if (processedNewsRepository.existsById(uniqueId)) {
            log.info("Этот пост уже был обработан (дубликат). Пропускаем.");
            return;
        }

        // Сохраняем новость как обработанную
        ProcessedNews processedNews = new ProcessedNews();
        processedNews.setUrlHash(uniqueId);
        processedNews.setOriginalUrl(url);
        processedNewsRepository.save(processedNews);

        log.info("Генерирую саммари для поста: {}", lastPostText.substring(0, Math.min(20, lastPostText.length())));

        String summary = openAIService.summarize(lastPostText, source.getSystemPrompt());

        PostQueue postQueue = new PostQueue();
        postQueue.setTargetChannel(targetChannel);
        postQueue.setContent(summary);
        postQueue.setPriority(0);
        postQueue.setScheduledTime(LocalDateTime.now());
        postQueue.setStatus(PostQueue.Status.PENDING);

        postQueueRepository.save(postQueue);

        log.info("Пост добавлен в очередь для канала {}!", targetChannel.getTitle());
    }
}