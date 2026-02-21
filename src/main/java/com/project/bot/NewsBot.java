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
import org.telegram.telegrambots.meta.api.methods.send.SendVideo; // <--- NEW
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
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
                Long targetId = Long.parseLong(callData.split("_")[2]);
                var sources = botService.getSourcesByTargetId(targetId);

                if (sources.isEmpty()) {
                    editMessage(chatId, messageId, "В этом канале пока нет источников.", keyboardService.getTargetChannelMenu(targetId));
                } else {
                    editMessage(chatId, messageId, "📋 Источники канала:",
                            keyboardService.getSourcesListKeyboard(sources));
                }
            }
            // 🗑 УДАЛИТЬ КАНАЛ (ДОБАВЬ ПОСЛЕ delete_)
            else if (callData.startsWith("delete_channel_")) {
                Long targetId = Long.parseLong(callData.split("_")[2]);

                try {
                    botService.deleteTargetChannel(targetId);
                    editMessage(chatId, messageId, "✅ Канал полностью удалён!\nПосты и источники тоже очищены.", null);
                } catch (Exception e) {
                    editMessage(chatId, messageId, "❌ Ошибка удаления: " + e.getMessage(), null);
                    return;
                }

                // Обновляем список
                var channels = botService.getAllTargets();
                if (channels.isEmpty()) {
                    editMessage(chatId, messageId, "📢 <b>Список каналов пуст.</b>", null);
                } else {
                    editMessage(chatId, messageId, "📢 <b>Твоя Сетка Каналов:</b>",
                            keyboardService.getTargetChannelsListKeyboard(channels));
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

            if (update.hasMyChatMember()) {
                var chatMember = update.getMyChatMember();
                String status = chatMember.getNewChatMember().getStatus();

                if ("administrator".equals(status)) {
                    String targetChatId = String.valueOf(chatMember.getChat().getId());
                    String title = chatMember.getChat().getTitle();
                    botService.addTargetChannel(targetChatId, title);
                }
            }

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

                SourceDraft draft = new SourceDraft();
                draft.setUrl(url);
                draft.setName(title);
                drafts.put(chatId, draft);

                List<TargetChannel> channels = botService.getAllTargets();
                if (channels.isEmpty()) {
                    sendText(chatId, "⚠️ Нет целевых каналов! Добавь меня админом в канал и напиши туда любое сообщение.");
                } else {
                    InlineKeyboardMarkup markup = keyboardService.getTargetChannelsKeyboard(channels);
                    sendTextWithMarkup(chatId, "🔗 Источник: <b>" + title + "</b>\nКуда будем публиковать новости?", markup);
                }
            }

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

    public void sendPhoto(long chatId, String imageUrl, String caption) {
        try {
            log.info("🖼️ Скачиваю и отправляю фото: {}", imageUrl);

            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.connect();

            if (conn.getResponseCode() == 200) {
                try (InputStream inputStream = conn.getInputStream()) {
                    SendPhoto photo = new SendPhoto();
                    photo.setChatId(String.valueOf(chatId));
                    photo.setPhoto(new InputFile(inputStream, "photo.jpg"));

                    if (caption.length() > 1024) {
                        caption = caption.substring(0, 1021) + "...";
                    }
                    photo.setCaption(caption);
                    photo.setParseMode("HTML");

                    execute(photo);
                    log.info("✅ Фото скачано и отправлено ({})", imageUrl);
                    return;
                }
            } else {
                log.warn("❌ HTTP {} для {}", conn.getResponseCode(), imageUrl);
            }
        } catch (Exception e) {
            log.error("❌ Скачивание фото не удалось: {}", e.getMessage());
        }

        // Фоллбэк — текст
        log.info("📝 Фоллбэк: отправляю текст");
        sendText(chatId, caption);
    }

    // --- НОВЫЙ МЕТОД ДЛЯ ВИДЕО ---
    public void sendVideo(long chatId, String videoUrl, String caption) {
        try {
            log.info("🎥 Скачиваю ВИДЕО ПОЛНОСТЬЮ: {}", videoUrl);

            // 1. Скачиваем ВСЁ видео в память/файл
            URL url = new URL(videoUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000); // 2 минуты на БОЛЬШОЕ видео
            conn.connect();

            if (conn.getResponseCode() == 200) {
                try (InputStream inputStream = conn.getInputStream()) {
                    // 2. Создаём SendVideo с InputStream
                    SendVideo video = new SendVideo();
                    video.setChatId(String.valueOf(chatId));

                    // КЛЮЧЕВОЕ: filename с .mp4 ОБЯЗАТЕЛЬНО!
                    InputFile videoFile = new InputFile(inputStream, "video_" + System.currentTimeMillis() + ".mp4");
                    video.setVideo(videoFile);

                    // 3. Обрезаем caption
                    if (caption.length() > 1024) {
                        caption = caption.substring(0, 1021) + "...";
                    }
                    video.setCaption(caption);
                    video.setParseMode("HTML");

                    // 4. ✅ Telegram сам сделает превью + плеер
                    video.setSupportsStreaming(true);

                    execute(video);
                    log.info("✅ ✅ ВИДЕО ОТПРАВЛЕНО ПОЛНОСТЬЮ! ({})", videoUrl);
                    return;
                }
            } else {
                log.warn("❌ HTTP {} для видео: {}", conn.getResponseCode(), videoUrl);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка sendVideo {}: {}", videoUrl, e.getMessage(), e);
        }

        // Фоллбэк: текст
        log.info("📝 Фоллбэк видео -> текст");
        sendText(chatId, caption);
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
        message.setParseMode("HTML");
        try { execute(message); } catch (TelegramApiException e) { log.error("Error sending text", e); }
    }

    public void sendTextWithMarkup(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);
        try { execute(message); } catch (TelegramApiException e) { log.error("Error", e); }
    }

    @Data
    private static class SourceDraft {
        private String url;
        private String name;
    }
}
