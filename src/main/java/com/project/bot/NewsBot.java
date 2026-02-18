package com.project.bot;

import com.project.config.BotConfig;
import com.project.entity.Source;
import com.project.entity.TargetChannel;
import com.project.repository.SourceRepository;
import com.project.repository.TargetChannelRepository;
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
    @Transactional
    @Override
    public void onUpdateReceived(Update update) {
        // --- 1. ОБРАБОТКА НАЖАТИЙ НА INLINE-КНОПКИ ---
        if (update.hasCallbackQuery()) {
            String callData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();

            if (callData.startsWith("source_")) {
                // Нажали на название источника -> Показываем детальное меню
                Long sourceId = Long.parseLong(callData.split("_")[1]);
                var sourceOpt = sourceRepository.findById(sourceId);

                if (sourceOpt.isPresent()) {
                    Source s = sourceOpt.get();
                    String text = "📡 <b>Источник:</b> " + s.getName() + "\n" +
                            "🔗 Ссылка: " + s.getUrl() + "\n" +
                            "🎯 Целевой канал: " + (s.getTargetChannel() != null ? s.getTargetChannel().getTitle() : "Нет");

                    // Редактируем сообщение: меняем список на инфо об источнике
                    editMessage(chatId, messageId, text, keyboardService.getSourceControlKeyboard(sourceId));
                }
            }
            else if (callData.startsWith("delete_")) {
                // Удаляем источник
                Long sourceId = Long.parseLong(callData.split("_")[1]);
                sourceRepository.deleteById(sourceId);

                // Возвращаемся к списку (обновленному)
                var sources = sourceRepository.findAll();
                editMessage(chatId, messageId, "✅ Источник удален.\nВыберите источник:", keyboardService.getSourcesListKeyboard(sources));
            }
            else if (callData.equals("back_to_list")) {
                // Возвращаемся к списку
                var sources = sourceRepository.findAll();
                editMessage(chatId, messageId, "📺 Ваши источники:", keyboardService.getSourcesListKeyboard(sources));
            }
            return; // Завершаем обработку колбэка
        }

        // --- 2. ОБРАБОТКА СООБЩЕНИЙ ---
        if (update.hasMessage()) {
            var message = update.getMessage();
            long chatId = message.getChatId();

            // Логика добавления ЦЕЛЕВОГО канала (бота добавили админом)
            if (update.hasMyChatMember()) {
                var chatMember = update.getMyChatMember();
                String status = chatMember.getNewChatMember().getStatus();

                if ("administrator".equals(status)) {
                    String targetChatId = String.valueOf(chatMember.getChat().getId());
                    String title = chatMember.getChat().getTitle();

                    if (targetChannelRepository.findByTelegramId(targetChatId).isEmpty()) {
                        TargetChannel target = new TargetChannel();
                        target.setTelegramId(targetChatId);
                        target.setTitle(title);
                        targetChannelRepository.save(target);
                        log.info("Новый целевой канал добавлен: {} ({})", title, targetChatId);
                    }
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

                String url = "https://t.me/s/" + username;

                boolean exists = sourceRepository.findByUrl(url).isPresent();

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
                        // Шлем сообщение с INLINE-КНОПКАМИ
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
