package com.project.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FingerprintService {

    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "не", "на", "я", "а", "с", "что", "он", "по", "это", "но", "как", "от", "до", "за",
            "со", "при", "для", "был", "была", "было", "нет", "да", "уже", "ещё",
            "срочно", "важно", "последние", "новости", "эксклюзив", "только", "сейчас", "читайте"
    );

    private static final int MAX_RECENT = 200;
    private static final double TITLE_SIMILARITY_THRESHOLD = 0.5; // 50% совпадение слов = дубль

    private final LinkedHashSet<String> recentFingerprints = new LinkedHashSet<>();
    private final LinkedHashSet<String> recentTitles = new LinkedHashSet<>(); // 🆕

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

    // 🆕 Проверка по схожести заголовка (ловит дубли из разных источников)
    public synchronized boolean isSimilarTitle(String title) {
        if (title == null || title.isBlank()) return false;
        Set<String> newWords = getSignificantWords(title);
        if (newWords.isEmpty()) return false;

        for (String existingTitle : recentTitles) {
            Set<String> existingWords = getSignificantWords(existingTitle);
            if (existingWords.isEmpty()) continue;
            long common = newWords.stream().filter(existingWords::contains).count();
            double similarity = (double) common / Math.max(newWords.size(), existingWords.size());
            if (similarity >= TITLE_SIMILARITY_THRESHOLD) {
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

    // 🆕 Сохраняем заголовок после добавления в очередь
    public synchronized void addTitle(String title) {
        if (title == null || title.isBlank()) return;
        if (recentTitles.size() >= MAX_RECENT) {
            recentTitles.remove(recentTitles.iterator().next());
        }
        recentTitles.add(title);
    }

    private Set<String> getSignificantWords(String text) {
        return Arrays.stream(text.toLowerCase().replaceAll("[^а-яёa-z0-9\\s]", " ").split("\\s+"))
                .filter(w -> w.length() > 3 && !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());
    }
}
