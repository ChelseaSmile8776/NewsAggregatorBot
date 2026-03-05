package com.project.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FingerprintService {

    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "не", "на", "я", "а", "с", "что", "он", "по", "это", "но", "как", "от", "до", "за",
            "со", "при", "для", "был", "была", "было", "нет", "да", "уже", "ещё",
            "срочно", "важно", "последние", "новости", "эксклюзив", "только", "сейчас", "читайте"
    );

    private static final int MAX_RECENT = 200;

    // LinkedHashSet — O(1) поиск + держит порядок вставки для удаления старых
    private final LinkedHashSet<String> recentFingerprints = new LinkedHashSet<>();

    public String createFingerprint(String text) {
        if (text == null || text.isBlank()) return "EMPTY";

        // Берём первые 200 символов — достаточно для уникальности
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

    public synchronized void addFingerprint(String fingerprint) {
        if (recentFingerprints.size() >= MAX_RECENT) {
            // удаляем самый старый элемент
            recentFingerprints.remove(recentFingerprints.iterator().next());
        }
        recentFingerprints.add(fingerprint);
    }
}
