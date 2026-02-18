package com.project.bot;

import com.project.config.BotConfig;
import com.project.entity.Source;
import com.project.entity.TargetChannel;
import com.project.repository.SourceRepository;
import com.project.repository.TargetChannelRepository;
import com.project.service.keyboard.KeyboardService;
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
    private final KeyboardService keyboardService; // <-- Добавь это поле и в конструктор!

    public NewsBot(BotConfig config, SourceRepository sourceRepository, TargetChannelRepository targetChannelRepository, KeyboardService keyboardService) {
        super(config.getBotToken());
        this.config = config;
        this.sourceRepository = sourceRepository;
        this.targetChannelRepository = targetChannelRepository;
        this.keyboardService = keyboardService;
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    // ... внутри класса NewsBot ...

    @Override
    public void onUpdateReceived(Update update) {
        // Логика добавления ЦЕЛЕВОГО канала (куда постим)
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

            // Логика добавления ИСТОЧНИКА (пересылка)
            if (message.getForwardFromChat() != null) {
                var channelChat = message.getForwardFromChat();
                String username = channelChat.getUserName();
                String title = channelChat.getTitle();

                if (username == null) {
                    sendText(chatId, "⚠️ Этот канал приватный. Нужна публичная ссылка (username).");
                    return;
                }

                String url = "https://t.me/s/" + username; // <-- Сразу ставим /s/ для парсинга

                boolean exists = sourceRepository.findByUrl(url).isPresent(); // Используем метод репозитория

                if (!exists) {
                    Source source = new Source();
                    source.setUrl(url);
                    source.setName(title);
                    source.setSystemPrompt("Ты новостной агрегатор.");

                    Optional<TargetChannel> defaultTarget = targetChannelRepository.findAll().stream().findFirst();
                    defaultTarget.ifPresent(source::setTargetChannel);

                    sourceRepository.save(source);
                    sendText(chatId, "✅ Источник добавлен: " + title + "\nПривязан к каналу: " + (defaultTarget.map(TargetChannel::getTitle).orElse("Нет целевых каналов!")));
                } else {
                    sendText(chatId, "⚠️ Этот источник уже есть в базе.");
                }
            }

            // ОБРАБОТКА КОМАНД И КНОПОК МЕНЮ
            if (message.hasText()) {
                String text = message.getText();

                if (text.equals("/start")) {
                    sendMenu(chatId, "👋 Добро пожаловать в Панель Управления!\n\nИспользуй кнопки ниже для навигации.");
                }
                else if (text.equals("📺 Мои Каналы")) {
                    var sources = sourceRepository.findAll();
                    if (sources.isEmpty()) {
                        sendText(chatId, "Список источников пуст.");
                    } else {
                        StringBuilder sb = new StringBuilder("📋 Твои источники:\n");
                        sources.forEach(s -> sb.append("🔹 ").append(s.getName()).append("\n   (").append(s.getUrl()).append(")\n"));
                        sendText(chatId, sb.toString());
                    }
                }
                else if (text.equals("📢 Сделать Пост")) {
                    sendText(chatId, "Функция 'Сделать Пост' в разработке... 🚧");
                }
                else if (text.equals("👥 Пользователи")) {
                    sendText(chatId, "Функция 'Пользователи' в разработке... 🚧");
                }
                else if (text.equals("⚙️ Настройки")) {
                    sendText(chatId, "Настройки пока недоступны.");
                }
                else if (text.equals("/list")) { // Оставим старую команду на всякий случай
                    sendText(chatId, "Используй кнопку '📺 Мои Каналы'!");
                }
            }
        }
    }

    // Метод для отправки сообщения с клавиатурой (МЕНЮ)
    public void sendMenu(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        // Получаем клавиатуру из сервиса
        message.setReplyMarkup(keyboardService.getMainMenu());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки меню: {}", e.getMessage());
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
