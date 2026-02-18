package com.project.bot;

import com.project.config.BotConfig;
import com.project.entity.Source;
import com.project.entity.TargetChannel;
import com.project.repository.SourceRepository;
import com.project.repository.TargetChannelRepository;
import com.project.service.BotService;
import com.project.service.keyboard.KeyboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Slf4j
@Component
public class NewsBot extends TelegramLongPollingBot {

    private final BotConfig config;
    private final SourceRepository sourceRepository;
    private final TargetChannelRepository targetChannelRepository;
    private final KeyboardService keyboardService; // <-- Добавь это поле и в конструктор!
    private final BotService botService;


    public NewsBot(BotConfig config, SourceRepository sourceRepository, TargetChannelRepository targetChannelRepository, KeyboardService keyboardService, BotService botService) {
        super(config.getBotToken());
        this.config = config;
        this.sourceRepository = sourceRepository;
        this.targetChannelRepository = targetChannelRepository;
        this.keyboardService = keyboardService;
        this.botService = botService;
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    // ... внутри класса NewsBot ...
    @Override
    // Убрал @Transactional отсюда, так как он тут не работает. Все транзакции внутри BotService.
    public void onUpdateReceived(Update update) {

        // --- 1. ОБРАБОТКА НАЖАТИЙ НА INLINE-КНОПКИ ---
        if (update.hasCallbackQuery()) {
            String callData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            if (callData.startsWith("source_")) {
                // ИСПРАВЛЕНИЕ: Используем сервис, чтобы избежать LazyInitializationException
                Long sourceId = Long.parseLong(callData.split("_")[1]);
                String text = botService.getSourceInfoText(sourceId); // <-- ВОТ ТУТ МЫ ЧИНИМ ОШИБКУ

                if (text != null) {
                    editMessage(chatId, messageId, text, keyboardService.getSourceControlKeyboard(sourceId));
                } else {
                    // Если вдруг источник удалили пока мы смотрели меню
                    editMessage(chatId, messageId, "⚠️ Источник не найден.", null);
                }
            }
            else if (callData.startsWith("delete_")) {
                Long sourceId = Long.parseLong(callData.split("_")[1]);
                botService.deleteSource(sourceId); // <-- Используем сервис для надежности

                // Возвращаемся к списку
                var sources = botService.getAllSources();
                editMessage(chatId, messageId, "✅ Источник удален.\nВыберите источник:", keyboardService.getSourcesListKeyboard(sources));
            }
            else if (callData.equals("back_to_list")) {
                var sources = botService.getAllSources(); // <-- Используем сервис
                editMessage(chatId, messageId, "📺 Ваши источники:", keyboardService.getSourcesListKeyboard(sources));
            }
            return;
        }

        // --- 2. ОБРАБОТКА СООБЩЕНИЙ ---
        if (update.hasMessage()) {
            var message = update.getMessage();
            long chatId = message.getChatId();

            // Логика добавления ЦЕЛЕВОГО канала
            if (update.hasMyChatMember()) {
                var chatMember = update.getMyChatMember();
                String status = chatMember.getNewChatMember().getStatus();

                if ("administrator".equals(status)) {
                    String targetChatId = String.valueOf(chatMember.getChat().getId());
                    String title = chatMember.getChat().getTitle();
                    botService.addTargetChannel(targetChatId, title); // <-- Сервис
                }
            }

            // Логика добавления ИСТОЧНИКА (пересылка)
            if (message.getForwardFromChat() != null) {
                var channelChat = message.getForwardFromChat();
                String username = channelChat.getUserName();
                String title = channelChat.getTitle();

                if (username == null) {
                    sendText(chatId, "⚠️ Этот канал приватный. Нужна публичная ссылка (username).");
                    return;
                }

                // Вся логика сохранения и проверок теперь внутри addSource
                // Это чище и надежнее
                String result = botService.addSource(username, title);
                sendText(chatId, result);
            }

            // ОБРАБОТКА КОМАНД И КНОПОК МЕНЮ
            if (message.hasText()) {
                String text = message.getText();

                if (text.equals("/start")) {
                    sendMenu(chatId, "👋 Добро пожаловать в Панель Управления!\n\nИспользуй кнопки ниже для навигации.");
                }
                else if (text.equals("📺 Мои Каналы")) {
                    var sources = botService.getAllSources(); // <-- Сервис
                    if (sources.isEmpty()) {
                        sendText(chatId, "Список источников пуст.");
                    } else {
                        SendMessage msg = new SendMessage();
                        msg.setChatId(String.valueOf(chatId));
                        msg.setText("Выберите источник для управления:");
                        msg.setReplyMarkup(keyboardService.getSourcesListKeyboard(sources));
                        try { execute(msg); } catch (Exception e) {}
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

    // Добавь этот метод в конец класса NewsBot
    private void editMessage(long chatId, int messageId, String text, InlineKeyboardMarkup markup) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText(text);
        edit.setParseMode("HTML"); // Чтобы работал жирный шрифт <b>
        edit.setReplyMarkup(markup);

        try {
            execute(edit);
        } catch (TelegramApiException e) {
            log.error("Ошибка редактирования сообщения: {}", e.getMessage());
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
