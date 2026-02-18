package com.project.bot;

import com.project.config.BotConfig;
import lombok.extern.slf4j.Slf4j; // Используем Slf4j
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j // Создает поле log
@Component
public class NewsBot extends TelegramLongPollingBot {

    private final BotConfig config;

    // Явный конструктор вместо @RequiredArgsConstructor, чтобы передать токен в родителя
    public NewsBot(BotConfig config) {
        super(config.getBotToken()); // Передаем токен в родительский конструктор
        this.config = config;
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (text.equals("/start")) {
                sendText(chatId, "Привет! Я бот-агрегатор новостей. Твой ID: " + chatId);
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

