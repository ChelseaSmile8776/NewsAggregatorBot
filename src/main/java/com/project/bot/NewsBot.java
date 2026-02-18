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
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (text.equals("/start")) {
                sendText(chatId, "Привет! Я бот-агрегатор.\nДобавь канал: /add_channel <ID> <Имя>");
            }

            // 3. ВОТ СЮДА ПИХАЕМ ЛОГИКУ СОХРАНЕНИЯ
            else if (text.startsWith("/add_channel")) {
                String[] parts = text.split(" ", 3); // Делим сообщение на 3 части

                if (parts.length < 3) {
                    sendText(chatId, "Ошибка! Формат: /add_channel <ID> <Имя>");
                    return;
                }

                String channelId = parts[1];
                String name = parts[2];

                // --- НАЧАЛО ВСТАВКИ ---
                Channel channel = new Channel();
                channel.setChannelId(channelId); // ID канала (например, -100123456)
                channel.setName(name);           // Имя (например, "CryptoNews")
                channel.setSystemPrompt("Ты новостной агрегатор. Сделай краткую выжимку."); // Дефолтный промпт
                channel.setSignature("С уважением, бот."); // Дефолтная подпись

                channelRepository.save(channel); // Сохраняем в базу!
                // --- КОНЕЦ ВСТАВКИ ---

                sendText(chatId, "Канал " + name + " успешно добавлен!");
            }

            else {
                sendText(chatId, "Я не знаю такую команду.");
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
