package com.project.service;

import com.project.entity.Source;
import com.project.entity.TargetChannel;
import com.project.repository.SourceRepository;
import com.project.repository.TargetChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotService {

    private final SourceRepository sourceRepository;
    private final TargetChannelRepository targetChannelRepository;

    @Transactional(readOnly = true)
    public String getSourceInfoText(Long sourceId) {
        return sourceRepository.findById(sourceId)
                .map(s -> {
                    String channelTitle = (s.getTargetChannel() != null) ? s.getTargetChannel().getTitle() : "Нет";
                    return "📡 <b>Источник:</b> " + s.getName() + "\n" +
                            "🔗 Ссылка: " + s.getUrl() + "\n" +
                            "🎯 Целевой канал: " + channelTitle;
                })
                .orElse(null);
    }

    @Transactional
    public void deleteSource(Long sourceId) {
        sourceRepository.deleteById(sourceId);
    }

    @Transactional(readOnly = true)
    public List<Source> getAllSources() {
        return sourceRepository.findAll();
    }

    // Метод добавления источника (возвращает текст ответа)
    @Transactional
    public String addSource(String username, String title) {
        String url = "https://t.me/s/" + username;

        if (sourceRepository.findByUrl(url).isPresent()) {
            return "⚠️ Этот источник уже есть в базе.";
        }

        Source source = new Source();
        source.setUrl(url);
        source.setName(title);
        source.setSystemPrompt("Ты новостной агрегатор.");

        // Привязываем к первому попавшемуся каналу (пока что)
        Optional<TargetChannel> defaultTarget = targetChannelRepository.findAll().stream().findFirst();
        defaultTarget.ifPresent(source::setTargetChannel);

        sourceRepository.save(source);

        String targetName = defaultTarget.map(TargetChannel::getTitle).orElse("Нет целевых каналов!");
        return "✅ Источник добавлен: " + title + "\nПривязан к каналу: " + targetName;
    }

    @Transactional
    public void addTargetChannel(String chatId, String title) {
        if (targetChannelRepository.findByTelegramId(chatId).isEmpty()) {
            TargetChannel target = new TargetChannel();
            target.setTelegramId(chatId);
            target.setTitle(title);
            targetChannelRepository.save(target);
            log.info("Новый целевой канал добавлен: {} ({})", title, chatId);
        }
    }
}
