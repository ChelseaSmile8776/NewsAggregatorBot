package com.project.scheduler;

import com.project.entity.PostQueue;
import com.project.entity.Source;
import com.project.repository.PostQueueRepository;
import com.project.service.BotService;
import com.project.service.OpenAIService;
import com.project.service.ParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsScheduler {

    private final BotService botService;
    private final ParserService parserService;
    private final OpenAIService openAiService;
    private final PostQueueRepository postQueueRepository;

    @Scheduled(fixedDelayString = "${scheduler.delay:900000}")
    public void processNews() {
        log.info("⏳ Запуск проверки новостей...");

        List<Source> sources = botService.getAllSources();

        for (Source source : sources) {
            if (source.getTargetChannel() == null) continue;

            try {
                // Парсим (возвращает ParsedPost)
                List<ParserService.ParsedPost> newPosts = parserService.parseNewPosts(source);

                if (newPosts.isEmpty()) continue;

                log.info("🔥 Найдено {} постов в '{}'", newPosts.size(), source.getName());

                for (ParserService.ParsedPost post : newPosts) {

                    String systemPrompt = (source.getSystemPrompt() != null && !source.getSystemPrompt().isEmpty())
                            ? source.getSystemPrompt()
                            : "Ты редактор Telegram-канала.";

                    // Рерайтим
                    String summary = openAiService.summarize(post.getText(), systemPrompt);

                    if (summary != null && !summary.contains("SKIP")) {
                        // СОХРАНЯЕМ В ОЧЕРЕДЬ
                        PostQueue queueItem = new PostQueue();
                        queueItem.setContent(summary);
                        queueItem.setImageUrl(post.getImageUrl()); // Сохраняем URL картинки
                        queueItem.setTargetChannel(source.getTargetChannel());
                        queueItem.setStatus(PostQueue.Status.PENDING);
                        queueItem.setScheduledTime(LocalDateTime.now());

                        postQueueRepository.save(queueItem);
                        log.info("📥 Добавлено в очередь: {}", source.getName());
                    } else {
                        log.info("🚫 Отсеяно (реклама/спам): {}", source.getName());
                    }
                }
            } catch (Exception e) {
                log.error("❌ Ошибка {}: {}", source.getName(), e.getMessage());
            }
        }
        log.info("✅ Цикл проверки завершен.");
    }
}
