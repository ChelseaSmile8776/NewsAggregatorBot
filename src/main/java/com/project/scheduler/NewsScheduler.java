package com.project.scheduler;

import com.project.bot.NewsBot;
import com.project.entity.Source;
import com.project.service.BotService;
import com.project.service.OpenAIService;
import com.project.service.ParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsScheduler {

    private final BotService botService;
    private final ParserService parserService;
    private final OpenAIService openAiService;
    private final NewsBot newsBot;

    // Запуск раз в 15 минут (900000 мс)
    @Scheduled(fixedDelayString = "${scheduler.delay:900000}")
    public void processNews() {
        log.info("⏳ Запуск проверки новостей...");

        List<Source> sources = botService.getAllSources();

        for (Source source : sources) {
            // Пропускаем источники без целевого канала
            if (source.getTargetChannel() == null) {
                continue;
            }

            try {
                // 1. Парсим новые посты
                List<String> newPosts = parserService.parseNewPosts(source);

                if (newPosts.isEmpty()) {
                    continue;
                }

                log.info("🔥 Найдено {} новых постов в '{}'", newPosts.size(), source.getName());

                // 2. Обрабатываем каждый пост
                for (String originalText : newPosts) {

                    String systemPrompt = (source.getSystemPrompt() != null && !source.getSystemPrompt().isEmpty())
                            ? source.getSystemPrompt()
                            : "Ты редактор Telegram-канала. Твоя задача — переписать новость кратко и интересно.";

                    // 3. Отправляем в OpenAI
                    String summary = openAiService.summarize(originalText, systemPrompt);

                    if (summary != null) {
                        try {
                            long targetChatId = Long.parseLong(source.getTargetChannel().getTelegramId());

                            // 4. Отправляем в канал
                            newsBot.sendText(targetChatId, summary);
                            log.info("✅ Опубликовано в канал: {}", source.getTargetChannel().getTitle());

                            // Пауза 5 сек
                            Thread.sleep(5000);

                        } catch (Exception e) {
                            log.error("❌ Ошибка отправки в канал {}: {}", source.getTargetChannel().getTitle(), e.getMessage());
                        }
                    }
                }

            } catch (Exception e) {
                log.error("❌ Ошибка обработки источника {}: {}", source.getName(), e.getMessage());
            }
        }
        log.info("✅ Проверка завершена.");
    }
}
