package com.project.bot;

import com.project.config.BotConfig;
import com.project.entity.Channel;
import com.project.repository.ChannelRepository; // <-- ИМПОРТ РЕПОЗИТОРИЯ
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
public class NewsBot extends TelegramLongPollingBot {

    private final BotConfig config;
    private final ChannelRepository channelRepository; // <-- 1. ДОБАВИЛИ ПОЛЕ

    // 2. ОБНОВИЛИ КОНСТРУКТОР (добавили channelRepository)
    public NewsBot(BotConfig config, ChannelRepository channelRepository) {
        super(config.getBotToken());
        this.config = config;
        this.channelRepository = channelRepository;
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            var message = update.getMessage();
            long chatId = message.getChatId();

            // 1. Если это пересланное сообщение из КАНАЛА
            if (message.getForwardFromChat() != null) {
                // ... внутри if (message.getForwardFromChat() != null) ...

                var channelChat = message.getForwardFromChat();
                String channelId = String.valueOf(channelChat.getId());
                String channelName = channelChat.getTitle();
                String username = channelChat.getUserName(); // <-- Получаем юзернейм

                // Проверка на null (у приватных каналов нет юзернейма)
                if (username == null) {
                    sendText(chatId, "⚠️ Этот канал приватный или у него нет ссылки. Я не смогу его читать.");
                    return;
                }

                if (channelRepository.findByChannelId(channelId).isEmpty()) {
                    Channel channel = new Channel();
                    channel.setChannelId(channelId);
                    channel.setName(channelName);
                    channel.setUsername(username); // <-- Сохраняем юзернейм
                    channel.setSystemPrompt("Ты новостной агрегатор.");
                    channelRepository.save(channel);

                    sendText(chatId, "✅ Канал добавлен!\nСсылка: @" + username);
                }

            }

            // 2. Обычные команды
            if (message.hasText()) {
                String text = message.getText();

                if (text.equals("/start")) {
                    sendText(chatId, "Привет! Перешли мне любой пост из канала, чтобы добавить его в базу.");
                } else if (text.equals("/list")) {
                    var channels = channelRepository.findAll();
                    if (channels.isEmpty()) {
                        sendText(chatId, "Список пуст.");
                    } else {
                        StringBuilder sb = new StringBuilder("📋 Твои каналы:\n");
                        channels.forEach(c -> sb.append(c.getName()).append(" (ID: ").append(c.getChannelId()).append(")\n"));
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
