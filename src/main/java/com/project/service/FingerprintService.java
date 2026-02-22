package com.project.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
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

    public String createFingerprint(String title, String text) {
        // Заголовок + первые 100 символов текста
        String content = title.toLowerCase() + " " +
                (text != null ? text.substring(0, Math.min(100, text.length())) : "").toLowerCase();

        // Убираем знаки препинания
        String clean = content.replaceAll("[^а-яёa-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Значимые слова > 3 букв, без стоп-слов
        List<String> words = Arrays.stream(clean.split(" "))
                .filter(word -> word.length() > 3)
                .filter(word -> !STOP_WORDS.contains(word))
                .map(String::trim)
                .filter(word -> !word.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // SHA-1 хеш
        String fingerprint = String.join("|", words);
        return fingerprint.length() > 0 ? DigestUtils.sha1Hex(fingerprint) : "EMPTY";
    }

    public boolean isDuplicate(String fingerprint, List<String> recentFingerprints) {
        return recentFingerprints.contains(fingerprint);
    }
}
