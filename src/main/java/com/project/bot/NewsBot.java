package com.project.bot;

import com.project.config.BotConfig;
import com.project.entity.TargetChannel;
import com.project.service.BotService;
import com.project.service.keyboard.KeyboardService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NewsBot extends TelegramLongPollingBot {

    private final BotConfig config;
    private final KeyboardService keyboardService;
    private final BotService botService;

    // Хранилище черновиков для новых источников (ChatId -> Draft)
    private final Map<Long, SourceDraft> drafts = new ConcurrentHashMap<>();

    public NewsBot(BotConfig config, KeyboardService keyboardService, BotService botService) {
        super(config.getBotToken());
        this.config = config;
        this.keyboardService = keyboardService;
        this.botService = botService;
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {

        // --- 0. ВАЖНО: ЛОВИМ КАНАЛ, КОГДА БОТ ВИДИТ В НЕМ ПОСТ ---
        if (update.hasChannelPost()) {
            var post = update.getChannelPost();
            String chatId = String.valueOf(post.getChatId());
            String title = post.getChat().getTitle();

            // Если бот видит пост, значит он там админ. Регистрируем!
            botService.addTargetChannel(chatId, title);
            return;
        }

        // --- 1. ОБРАБОТКА НАЖАТИЙ НА INLINE-КНОПКИ ---
        if (update.hasCallbackQuery()) {
            String callData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            // --- A. УПРАВЛЕНИЕ СЕТКОЙ КАНАЛОВ ---

            if (callData.startsWith("mychannel_")) {
                Long targetId = Long.parseLong(callData.split("_")[1]);
                TargetChannel ch = botService.getTargetChannel(targetId);

                if (ch != null) {
                    editMessage(chatId, messageId, "📢 <b>Управление каналом:</b> " + ch.getTitle(),
                            keyboardService.getTargetChannelMenu(targetId));
                } else {
                    editMessage(chatId, messageId, "⚠️ Канал не найден (возможно, удален).", null);
                }
            }
            else if (callData.startsWith("channel_sources_")) {
                // Берем [2], так как формат channel_sources_ID
                Long targetId = Long.parseLong(callData.split("_")[2]);
                var sources = botService.getSourcesByTargetId(targetId);

                if (sources.isEmpty()) {
                    editMessage(chatId, messageId, "В этом канале пока нет источников.", keyboardService.getTargetChannelMenu(targetId));
                } else {
                    editMessage(chatId, messageId, "📋 Источники канала:",
                            keyboardService.getSourcesListKeyboard(sources));
                }
            }
            else if (callData.equals("back_to_channels")) {
                var channels = botService.getAllTargets();
                editMessage(chatId, messageId, "📢 <b>Твоя Сетка Каналов:</b>", keyboardService.getTargetChannelsListKeyboard(channels));
            }

            // --- B. УПРАВЛЕНИЕ КОНКРЕТНЫМ ИСТОЧНИКОМ ---

            else if (callData.startsWith("source_")) {
                Long sourceId = Long.parseLong(callData.split("_")[1]);
                String text = botService.getSourceInfoText(sourceId);

                if (text != null) {
                    editMessage(chatId, messageId, text, keyboardService.getSourceControlKeyboard(sourceId));
                } else {
                    editMessage(chatId, messageId, "⚠️ Источник не найден.", null);
                }
            }
            else if (callData.startsWith("delete_")) {
                Long sourceId = Long.parseLong(callData.split("_")[1]);
                botService.deleteSource(sourceId);
                editMessage(chatId, messageId, "✅ Источник успешно удален.", null);
            }
            else if (callData.equals("back_to_list")) {
                var channels = botService.getAllTargets();
                editMessage(chatId, messageId, "📢 <b>Твоя Сетка Каналов:</b>", keyboardService.getTargetChannelsListKeyboard(channels));
            }

            // --- C. ДОБАВЛЕНИЕ НОВОГО ИСТОЧНИКА (ВЫБОР КАНАЛА) ---
            else if (callData.startsWith("target_")) {
                Long targetId = Long.parseLong(callData.split("_")[1]);
                SourceDraft draft = drafts.get(chatId);

                if (draft != null) {
                    botService.addSourceWithTarget(draft.getUrl(), draft.getName(), targetId);
                    drafts.remove(chatId);
                    editMessage(chatId, messageId, "✅ Источник <b>" + draft.getName() + "</b> успешно добавлен!", null);
                } else {
                    editMessage(chatId, messageId, "⚠️ Ошибка: данные устарели.", null);
                }
            }
            return;
        }

        // --- 2. ОБРАБОТКА СООБЩЕНИЙ ---
        if (update.hasMessage()) {
            var message = update.getMessage();
            long chatId = message.getChatId();

            // Логика добавления ЦЕЛЕВОГО канала (Событие добавления бота в админы)
            if (update.hasMyChatMember()) {
                var chatMember = update.getMyChatMember();
                String status = chatMember.getNewChatMember().getStatus();

                if ("administrator".equals(status)) {
                    String targetChatId = String.valueOf(chatMember.getChat().getId());
                    String title = chatMember.getChat().getTitle();
                    botService.addTargetChannel(targetChatId, title);
                }
            }

            // Логика добавления ИСТОЧНИКА (пересылка поста)
            if (message.getForwardFromChat() != null) {
                var channelChat = message.getForwardFromChat();
                String username = channelChat.getUserName();
                String title = channelChat.getTitle();

                if (username == null) {
                    sendText(chatId, "⚠️ Этот канал приватный или у него нет ссылки. Нужна публичная ссылка (username).");
                    return;
                }

                String url = "https://t.me/s/" + username;

                if (botService.existsByUrl(url)) {
                    sendText(chatId, "⚠️ Этот источник уже есть в базе.");
                    return;
                }

                // 1. СОЗДАЕМ ЧЕРНОВИК
                SourceDraft draft = new SourceDraft();
                draft.setUrl(url);
                draft.setName(title);
                drafts.put(chatId, draft);

                // 2. СПРАШИВАЕМ КУДА ДОБАВИТЬ
                List<TargetChannel> channels = botService.getAllTargets();
                if (channels.isEmpty()) {
                    sendText(chatId, "⚠️ Нет целевых каналов! Добавь меня админом в канал и напиши туда любое сообщение.");
                } else {
                    InlineKeyboardMarkup markup = keyboardService.getTargetChannelsKeyboard(channels);
                    sendTextWithMarkup(chatId, "🔗 Источник: <b>" + title + "</b>\nКуда будем публиковать новости?", markup);
                }
            }

            // ОБРАБОТКА ТЕКСТОВЫХ КОМАНД
            if (message.hasText()) {
                String text = message.getText();

                if (text.equals("/start")) {
                    sendMenu(chatId, "👋 Добро пожаловать в Панель Управления!");
                }
                else if (text.equals("📺 Мои Каналы")) {
                    var channels = botService.getAllTargets();
                    if (channels.isEmpty()) {
                        sendText(chatId, "Список каналов пуст. Добавь бота админом в канал и напиши туда тест.");
                    } else {
                        SendMessage msg = new SendMessage();
                        msg.setChatId(String.valueOf(chatId));
                        msg.setText("📢 <b>Твоя Сетка Каналов:</b>\nВыберите канал для управления:");
                        msg.setReplyMarkup(keyboardService.getTargetChannelsListKeyboard(channels));
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

    // Добавить импорты:
    // import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
    // import org.telegram.telegrambots.meta.api.objects.InputFile;

    public void sendPhoto(long chatId, String imageUrl, String caption) {
        SendPhoto photo = new SendPhoto();
        photo.setChatId(String.valueOf(chatId));
        photo.setPhoto(new InputFile(imageUrl));

        // Обрезаем подпись, если она слишком длинная (лимит Telegram 1024 символа для фото)
        if (caption.length() > 1024) {
            caption = caption.substring(0, 1021) + "...";
        }
        photo.setCaption(caption);

        // ВАЖНО: Включаем HTML
        photo.setParseMode("HTML");

        try {
            execute(photo);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки фото: {}", e.getMessage());
            // Если фото не отправилось (битая ссылка), пробуем отправить просто текст
            sendText(chatId, caption);
        }
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ---

    public void sendMenu(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(keyboardService.getMainMenu());
        try { execute(message); } catch (TelegramApiException e) { log.error("Error", e); }
    }

    private void editMessage(long chatId, int messageId, String text, InlineKeyboardMarkup markup) {
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setText(text);
        edit.setParseMode("HTML");
        edit.setReplyMarkup(markup);
        try { execute(edit); } catch (TelegramApiException e) { log.error("Error", e); }
    }

    public void sendText(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        // ВАЖНО: Включаем HTML, чтобы работали теги <b> и <i>
        message.setParseMode("HTML");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending text", e);
        }
    }


    public void sendTextWithMarkup(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);
        try { execute(message); } catch (TelegramApiException e) { log.error("Error", e); }
    }

    // Внутренний класс для черновика (DTO)
    @Data
    private static class SourceDraft {
        private String url;
        private String name;
    }
}
