package com.project.scheduler;

import com.project.entity.PostQueue;
import com.project.entity.Source;
import com.project.repository.PostQueueRepository;
import com.project.service.BotService;
import com.project.service.FingerprintService;
import com.project.service.OpenAIService;
import com.project.service.ParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsScheduler {

    private final BotService botService;
    private final ParserService parserService;
    private final OpenAIService openAiService;
    private final PostQueueRepository postQueueRepository;
    private final FingerprintService fingerprintService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${scheduler.delay:900000}")
    public void processNews() {
        if (!running.compareAndSet(false, true)) {
            log.warn("⛔ processNews уже выполняется, пропускаю этот запуск");
            return;
        }

        try {
            log.info("⏳ Запуск проверки новостей...");

            List<Source> sources = botService.getAllSources();

            for (Source source : sources) {
                if (source.getTargetChannel() == null) continue;

                try {
                    List<ParserService.ParsedPost> newPosts = parserService.parseNewPosts(source);
                    if (newPosts.isEmpty()) continue;

                    log.info("🔥 Найдено {} постов в '{}'", newPosts.size(), source.getName());

                    Set<Integer> seenPostIds = new HashSet<>();
                    LocalDateTime baseTime = LocalDateTime.now();
                    int secOffset = 0;

                    for (ParserService.ParsedPost parsedPost : newPosts) {
                        if (!seenPostIds.add(parsedPost.getPostId())) continue;

                        // 🚫 Скипаем видео без прямой ссылки — нет смысла постить трейлер без видео
                        if (parsedPost.isVideo() && parsedPost.getImageUrl() == null) {
                            log.info("⏭️ Пропущено: видео без прямой ссылки — {} (postId={})",
                                    source.getName(), parsedPost.getPostId());
                            continue;
                        }

                        // Проверка по заголовку ДО вызова GPT — экономим токены
                        if (fingerprintService.isSimilarTitle(parsedPost.getOriginalTitle())) {
                            log.info("⏭️ Дубликат по заголовку пропущен: {} (postId={})",
                                    source.getName(), parsedPost.getPostId());
                            continue;
                        }

                        String systemPrompt = (source.getSystemPrompt() != null && !source.getSystemPrompt().isEmpty())
                                ? source.getSystemPrompt()
                                : "Ты редактор Telegram-канала.";

                        String summary = openAiService.summarize(parsedPost.getText(), systemPrompt);
                        if (summary == null || summary.trim().isEmpty()) continue;

                        String upper = summary.trim().toUpperCase(Locale.ROOT);
                        if (upper.contains("SKIP")) {
                            log.info("🚫 Отсеяно GPT (реклама/спам): {} (postId={})",
                                    source.getName(), parsedPost.getPostId());
                            continue;
                        }

                        // Проверяем дубликат по фингерпринту саммари
                        String fingerprint = fingerprintService.createFingerprint(summary);
                        if (fingerprintService.isDuplicate(fingerprint)) {
                            log.info("⏭️ Дубликат по фингерпринту пропущен: {} (postId={})",
                                    source.getName(), parsedPost.getPostId());
                            continue;
                        }

                        fingerprintService.addFingerprint(fingerprint);
                        fingerprintService.addTitle(parsedPost.getOriginalTitle());

                        PostQueue queueItem = new PostQueue();
                        queueItem.setContent(summary.trim());
                        queueItem.setImageUrl(parsedPost.getImageUrl());
                        queueItem.setTargetChannel(source.getTargetChannel());
                        queueItem.setPriority(1);
                        queueItem.setStatus(PostQueue.Status.PENDING);
                        queueItem.setScheduledTime(baseTime.plusSeconds(secOffset++));

                        postQueueRepository.save(queueItem);

                        log.info("📥 Добавлено в очередь: {} (postId={}, image={})",
                                source.getName(), parsedPost.getPostId(), parsedPost.getImageUrl());
                    }

                } catch (Exception e) {
                    log.error("❌ Ошибка {}: {}", source.getName(), e.getMessage(), e);
                }
            }

            log.info("✅ Цикл проверки завершен.");
        } finally {
            running.set(false);
        }
    }
}
