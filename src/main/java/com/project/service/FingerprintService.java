package com.project.service;

import com.project.entity.PostQueue;
import com.project.repository.PostQueueRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FingerprintService {

    private final PostQueueRepository postQueueRepository;

    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "не", "на", "я", "а", "с", "что", "он", "по", "это", "но", "как", "от", "до", "за",
            "со", "при", "для", "был", "была", "было", "нет", "да", "уже", "ещё",
            "срочно", "важно", "последние", "новости", "эксклюзив", "только", "сейчас", "читайте"
    );

    private static final int MAX_RECENT = 200;
    private static final double TITLE_SIMILARITY_THRESHOLD = 0.5;
    private static final int DEDUP_WINDOW_HOURS = 6;

    private final LinkedHashSet<String> recentFingerprints = new LinkedHashSet<>();
    private final LinkedHashSet<String> recentTitles = new LinkedHashSet<>();

    public String createFingerprint(String text) {
        if (text == null || text.isBlank()) return "EMPTY";

        String content = text.substring(0, Math.min(200, text.length())).toLowerCase();

        String clean = content.replaceAll("[^а-яёa-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        List<String> words = Arrays.stream(clean.split(" "))
                .filter(w -> w.length() > 3)
                .filter(w -> !STOP_WORDS.contains(w))
                .map(String::trim)
                .filter(w -> !w.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (words.isEmpty()) return "EMPTY";

        return DigestUtils.sha1Hex(String.join("|", words));
    }

    public synchronized boolean isDuplicate(String fingerprint) {
        return recentFingerprints.contains(fingerprint);
    }

    /**
     * Проверяет схожесть заголовка:
     * 1. Сначала по in-memory кэшу (текущий цикл)
     * 2. Затем по последним SENT постам из БД за последние 6 часов
     */
    public synchronized boolean isSimilarTitle(String title) {
        if (title == null || title.isBlank()) return false;
        Set<String> newWords = getSignificantWords(title);
        if (newWords.isEmpty()) return false;

        // Шаг 1: проверка по in-memory (текущий цикл парсинга)
        for (String existingTitle : recentTitles) {
            if (isSimilar(newWords, getSignificantWords(existingTitle))) {
                return true;
            }
        }

        // Шаг 2: проверка по недавно отправленным постам из БД
        List<PostQueue> recentSent = postQueueRepository.findRecentSent(
                LocalDateTime.now().minusHours(DEDUP_WINDOW_HOURS)
        );
        for (PostQueue sent : recentSent) {
            if (sent.getContent() == null) continue;
            // Берём первую строку контента как заголовок
            String sentTitle = extractTitle(sent.getContent());
            if (isSimilar(newWords, getSignificantWords(sentTitle))) {
                return true;
            }
        }

        return false;
    }

    public synchronized void addFingerprint(String fingerprint) {
        if (recentFingerprints.size() >= MAX_RECENT) {
            recentFingerprints.remove(recentFingerprints.iterator().next());
        }
        recentFingerprints.add(fingerprint);
    }

    public synchronized void addTitle(String title) {
        if (title == null || title.isBlank()) return;
        if (recentTitles.size() >= MAX_RECENT) {
            recentTitles.remove(recentTitles.iterator().next());
        }
        recentTitles.add(title);
    }

    private boolean isSimilar(Set<String> wordsA, Set<String> wordsB) {
        if (wordsB.isEmpty()) return false;
        long common = wordsA.stream().filter(wordsB::contains).count();
        double similarity = (double) common / Math.max(wordsA.size(), wordsB.size());
        return similarity >= TITLE_SIMILARITY_THRESHOLD;
    }

    /**
     * Извлекает первую строку контента (заголовок поста GPT-саммари)
     */
    private String extractTitle(String content) {
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim().replaceAll("[*_#]", "");
            if (!trimmed.isEmpty()) return trimmed;
        }
        return content.substring(0, Math.min(100, content.length()));
    }

    private Set<String> getSignificantWords(String text) {
        return Arrays.stream(text.toLowerCase().replaceAll("[^а-яёa-z0-9\\s]", " ").split("\\s+"))
                .filter(w -> w.length() > 3 && !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());
    }
}
