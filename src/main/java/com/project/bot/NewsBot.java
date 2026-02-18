package com.project.bot;

import com.project.config.BotConfig;
import com.project.entity.Source;
import com.project.entity.TargetChannel;
import com.project.repository.SourceRepository;
import com.project.repository.TargetChannelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Slf4j
@Component
public class NewsBot extends TelegramLongPollingBot {

    private final BotConfig config;
    private final SourceRepository sourceRepository;
    private final TargetChannelRepository targetChannelRepository;

    public NewsBot(BotConfig config, SourceRepository sourceRepository, TargetChannelRepository targetChannelRepository) {
        super(config.getBotToken());
        this.config = config;
        this.sourceRepository = sourceRepository;
        this.targetChannelRepository = targetChannelRepository;
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Логика добавления ЦЕЛЕВОГО канала (куда постим)
        // Бот должен быть админом в канале
        if (update.hasMyChatMember()) {
            var chatMember = update.getMyChatMember();
            String status = chatMember.getNewChatMember().getStatus();

            if ("administrator".equals(status)) {
                String chatId = String.valueOf(chatMember.getChat().getId());
                String title = chatMember.getChat().getTitle();

                if (targetChannelRepository.findByTelegramId(chatId).isEmpty()) {
                    TargetChannel target = new TargetChannel();
                    target.setTelegramId(chatId);
                    target.setTitle(title);
                    targetChannelRepository.save(target);
                    log.info("Новый целевой канал добавлен: {} ({})", title, chatId);
                }
            }
        }

        if (update.hasMessage()) {
            var message = update.getMessage();
            long chatId = message.getChatId();

            // Логика добавления ИСТОЧНИКА (откуда берем)
            // Пользователь пересылает пост из канала-источника
            if (message.getForwardFromChat() != null) {
                var channelChat = message.getForwardFromChat();
                String username = channelChat.getUserName(); // Получаем юзернейм (t.me/username)
                String title = channelChat.getTitle();

                if (username == null) {
                    sendText(chatId, "⚠️ Этот канал приватный. Нужна публичная ссылка (username).");
                    return;
                }

                String url = "https://t.me/" + username;

                // Проверяем, есть ли уже такой источник
                // (Предполагаем, что в SourceRepository ты добавил метод findByUrl, если нет - добавь)
                // Если метода нет, можно пока искать перебором, но лучше добавить в репозиторий.
                // Пока сделаем простую проверку:
                boolean exists = sourceRepository.findAll().stream()
                        .anyMatch(s -> s.getUrl().equals(url));

                if (!exists) {
                    Source source = new Source();
                    source.setUrl(url);
                    source.setName(title);
                    source.setSystemPrompt("Ты новостной агрегатор.");

                    // ВАЖНО: Пока привязываем к первому попавшемуся целевому каналу (или null)
                    // В будущем тут будет меню выбора "Куда привязать?"
                    Optional<TargetChannel> defaultTarget = targetChannelRepository.findAll().stream().findFirst();
                    defaultTarget.ifPresent(source::setTargetChannel);

                    sourceRepository.save(source);
                    sendText(chatId, "✅ Источник добавлен: " + title + "\nПривязан к каналу: " + (defaultTarget.map(TargetChannel::getTitle).orElse("Нет целевых каналов!")));
                } else {
                    sendText(chatId, "⚠️ Этот источник уже есть в базе.");
                }
            }

            // Обычные команды
            if (message.hasText()) {
                String text = message.getText();

                if (text.equals("/start")) {
                    sendText(chatId, "Привет! \n1. Добавь меня админом в ТВОЙ канал.\n2. Перешли мне пост из ЧУЖОГО канала, чтобы я начал его читать.");
                } else if (text.equals("/list")) {
                    var sources = sourceRepository.findAll();
                    if (sources.isEmpty()) {
                        sendText(chatId, "Список источников пуст.");
                    } else {
                        StringBuilder sb = new StringBuilder("📋 Твои источники:\n");
                        sources.forEach(s -> sb.append(s.getName()).append(" -> ").append(s.getUrl()).append("\n"));
                        sendText(chatId, sb.toString());
                    }
                }
            }
        }
    }

    public void sendText(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage());
        }
    }
}
