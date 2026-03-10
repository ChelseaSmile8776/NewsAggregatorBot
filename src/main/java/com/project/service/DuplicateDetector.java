package com.project.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class DuplicateDetector {

    private static final Duration TTL = Duration.ofHours(6);
    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "на", "с", "о", "для", "по", "от", "из", "что",
            "как", "это", "уже", "при", "об", "со", "до", "за", "под",
            "the", "a", "an", "to", "of", "for", "in", "is", "are", "with"
    );

    // channelTitle -> (bucket -> время добавления)
    private final Map<String, Map<String, Instant>> channelBuckets = new ConcurrentHashMap<>();

    /**
     * Проверяет, был ли похожий заголовок в данном канале за последние 6 часов.
     * Вызывать ДО GPT, с оригинальным заголовком.
     */
    public boolean isDuplicate(String channelTitle, String rawTitle) {
        String bucket = makeBucket(rawTitle);
        if (bucket == null) return false;

        Map<String, Instant> buckets = channelBuckets
                .computeIfAbsent(channelTitle, k -> new ConcurrentHashMap<>());

        Instant now = Instant.now();
        // Чистим устаревшие записи
        buckets.entrySet().removeIf(e -> e.getValue().isBefore(now.minus(TTL)));

        Instant lastSeen = buckets.get(bucket);
        if (lastSeen != null) {
            log.info("🔁 DuplicateDetector: канал='{}' bucket='{}' уже видели {}m назад",
                    channelTitle, bucket,
                    Duration.between(lastSeen, now).toMinutes());
            return true;
        }

        buckets.put(bucket, now);
        return false;
    }

    private String makeBucket(String title) {
        if (title == null || title.isBlank()) return null;
        List<String> words = Arrays.stream(title.toLowerCase().split("\\W+"))
                .filter(w -> w.length() > 3)
                .filter(w -> !STOP_WORDS.contains(w))
                .sorted()
                .limit(3)
                .toList();
        // Нужно хотя бы 2 слова для надёжного матча
        return words.size() >= 2 ? String.join("+", words) : null;
    }
}
