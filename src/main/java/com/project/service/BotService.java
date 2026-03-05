package com.project.service;

import com.project.entity.PostQueue;
import com.project.entity.Source;
import com.project.entity.TargetChannel;
import com.project.repository.PostQueueRepository;
import com.project.repository.SourceRepository;
import com.project.repository.TargetChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotService {

    private final SourceRepository sourceRepository;
    private final TargetChannelRepository targetChannelRepository;
    private final PostQueueRepository postQueueRepository;
    private final FingerprintService fingerprintService;

    @Transactional(readOnly = true)
    public String getSourceInfoText(Long sourceId) {
        return sourceRepository.findById(sourceId)
                .map(s -> {
                    String channelTitle = (s.getTargetChannel() != null)
                            ? s.getTargetChannel().getTitle()
                            : "Нет";
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

    @Transactional(readOnly = true)
    public List<Source> getSourcesByTargetId(Long targetId) {
        return sourceRepository.findAll().stream()
                .filter(s -> s.getTargetChannel() != null
                        && s.getTargetChannel().getId().equals(targetId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TargetChannel> getAllTargets() {
        return targetChannelRepository.findAll();
    }

    @Transactional(readOnly = true)
    public TargetChannel getTargetChannel(Long id) {
        return targetChannelRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean existsByUrl(String url) {
        return sourceRepository.findByUrl(url).isPresent();
    }

    @Transactional
    public void addSourceWithTarget(String url, String name, Long targetId) {
        TargetChannel target = targetChannelRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Target channel not found"));

        Source source = new Source();
        source.setUrl(url);
        source.setName(name);
        source.setSystemPrompt("Ты новостной агрегатор.");
        source.setTargetChannel(target);

        sourceRepository.save(source);
    }

    @Transactional
    public void deleteTargetChannel(Long targetId) {
        postQueueRepository.deleteByTargetChannelId(targetId);

        sourceRepository.findAll().stream()
                .filter(s -> s.getTargetChannel() != null
                        && s.getTargetChannel().getId().equals(targetId))
                .forEach(s -> sourceRepository.deleteById(s.getId()));

        targetChannelRepository.deleteById(targetId);

        log.info("✅ Канал {} удалён полностью!", targetId);
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

    @Transactional
    public boolean checkAndCreatePost(Source source,
                                      String content,
                                      String imageUrl) {

        String fingerprint = fingerprintService.createFingerprint(content);

        if (fingerprintService.isDuplicate(fingerprint)) {
            log.info("⏭️ Дубликат пропущен: {}...", fingerprint.substring(0, 8));
            return false;
        }

        PostQueue post = new PostQueue();
        post.setContent(content);
        post.setImageUrl(imageUrl);
        post.setTargetChannel(source.getTargetChannel());
        post.setStatus(PostQueue.Status.PENDING);
        post.setPriority(0);
        post.setScheduledTime(LocalDateTime.now().plusMinutes(5));

        postQueueRepository.save(post);

        fingerprintService.addFingerprint(fingerprint);

        log.info("✅ Новый уникальный пост добавлен в очередь: {}...", fingerprint.substring(0, 8));
        return true;
    }
}
