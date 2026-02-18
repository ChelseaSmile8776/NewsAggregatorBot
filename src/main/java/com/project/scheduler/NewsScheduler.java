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

    @Scheduled(fixedDelayString = "${scheduler.delay:900000}")
    public void processNews() {
        log.info("⏳ Запуск проверки новостей...");

        List<Source> sources = botService.getAllSources();

        for (Source source : sources) {
            if (source.getTargetChannel() == null) continue;

            try {
                List<String> newPosts = parserService.parseNewPosts(source);

                if (newPosts.isEmpty()) continue;

                log.info("🔥 Найдено {} потенциальных постов в '{}'", newPosts.size(), source.getName());

                for (String originalText : newPosts) {

                    String systemPrompt = (source.getSystemPrompt() != null && !source.getSystemPrompt().isEmpty())
                            ? source.getSystemPrompt()
                            : "Ты редактор Telegram-канала.";

                    String summary = openAiService.summarize(originalText, systemPrompt);

                    // Проверка на рекламу (SKIP)
                    if (summary != null && !summary.contains("SKIP")) {
                        try {
                            long targetChatId = Long.parseLong(source.getTargetChannel().getTelegramId());
                            newsBot.sendText(targetChatId, summary);
                            log.info("✅ Опубликовано в канал: {}", source.getTargetChannel().getTitle());
                            Thread.sleep(5000);
                        } catch (Exception e) {
                            log.error("❌ Ошибка отправки: {}", e.getMessage());
                        }
                    } else {
                        log.info("🚫 Реклама или мусор отсеяны AI: {}", source.getName());
                    }
                }

            } catch (Exception e) {
                log.error("❌ Ошибка источника {}: {}", source.getName(), e.getMessage());
            }
        }
        log.info("✅ Проверка завершена.");
    }
}
