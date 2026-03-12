package com.project.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DuplicateDetector {

    private static final Duration TTL = Duration.ofHours(6);
    private static final double JACCARD_THRESHOLD = 0.4;

    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "на", "с", "о", "для", "по", "от", "из", "что",
            "как", "это", "уже", "при", "об", "со", "до", "за", "под",
            "the", "a", "an", "to", "of", "for", "in", "is", "are", "with",
            "правда", "слух", "итоги", "обзор", "главное", "новость",
            "новости", "стал", "стала", "первый", "первая", "первым",
            "запускает", "подал", "заявку", "собрал", "превысила", "свыше"
    );

    // Только разноалфавитные синонимы — остальное Jaccard поймает сам
    private static final Map<String, String> SYNONYMS = Map.of(
            "биткоин", "btc",
            "bitcoin", "btc",
            "битка", "btc",
            "ethereum", "eth",
            "эфириум", "eth",
            "блэкрок", "blackrock",
            "elon", "маск",
            "миллион", "млн",
            "миллионов", "млн"
    );

    private final Map<String, Map<String, Instant>> channelBuckets = new ConcurrentHashMap<>();

    public boolean isDuplicate(String channelTitle, String rawTitle) {
        Set<String> tokens = makeTokens(rawTitle);
        if (tokens.size() < 2) return false;

        Map<String, Instant> buckets = channelBuckets
                .computeIfAbsent(channelTitle, k -> new ConcurrentHashMap<>());

        Instant now = Instant.now();
        buckets.entrySet().removeIf(e -> e.getValue().isBefore(now.minus(TTL)));

        for (Map.Entry<String, Instant> entry : buckets.entrySet()) {
            Set<String> existing = new HashSet<>(Arrays.asList(entry.getKey().split("\\+")));

            Set<String> intersection = new HashSet<>(tokens);
            intersection.retainAll(existing);

            Set<String> union = new HashSet<>(tokens);
            union.addAll(existing);

            double jaccard = (double) intersection.size() / union.size();

            if (jaccard >= JACCARD_THRESHOLD) {
                log.info("🔁 DuplicateDetector: канал='{}' jaccard={} tokens='{}' existing='{}' {}m назад",
                        channelTitle, String.format("%.2f", jaccard), tokens, existing,
                        Duration.between(entry.getValue(), now).toMinutes());
                return true;
            }
        }

        String bucket = String.join("+", tokens.stream().sorted().toList());
        buckets.put(bucket, now);
        return false;
    }

    private Set<String> makeTokens(String title) {
        if (title == null || title.isBlank()) return Set.of();
        return Arrays.stream(title.toLowerCase().split("\\W+"))
                .filter(w -> w.length() > 2)
                .filter(w -> !STOP_WORDS.contains(w))
                .map(w -> SYNONYMS.getOrDefault(w, w))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
