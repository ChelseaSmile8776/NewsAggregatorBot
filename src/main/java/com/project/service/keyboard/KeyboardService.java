package com.project.service.keyboard;

import com.project.entity.Source;
import com.project.entity.TargetChannel;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Service
public class KeyboardService {

    // ГЛАВНОЕ МЕНЮ (REPLY) - БЕЗ ИЗМЕНЕНИЙ
    public ReplyKeyboardMarkup getMainMenu() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setSelective(true);
        markup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📺 Мои Каналы");
        row1.add("📢 Сделать Пост");
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("👥 Пользователи");
        row2.add("⚙️ Настройки");
        keyboard.add(row2);

        markup.setKeyboard(keyboard);
        return markup;
    }

    // СПИСОК ИСТОЧНИКОВ - БЕЗ ИЗМЕНЕНИЙ
    public InlineKeyboardMarkup getSourcesListKeyboard(List<Source> sources) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Source source : sources) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(source.getName());
            button.setCallbackData("source_" + source.getId());
            row.add(button);
            rows.add(row);
        }
        markup.setKeyboard(rows);
        return markup;
    }

    // УПРАВЛЕНИЕ ИСТОЧНИКОМ - БЕЗ ИЗМЕНЕНИЙ
    public InlineKeyboardMarkup getSourceControlKeyboard(Long sourceId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton();
        deleteBtn.setText("🗑 Удалить");
        deleteBtn.setCallbackData("delete_" + sourceId);
        row1.add(deleteBtn);
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("🔙 Назад к списку");
        backBtn.setCallbackData("back_to_list");
        row2.add(backBtn);
        rows.add(row2);

        markup.setKeyboard(rows);
        return markup;
    }

    // ВЫБОР ЦЕЛЕВОГО КАНАЛА - БЕЗ ИЗМЕНЕНИЙ
    public InlineKeyboardMarkup getTargetChannelsKeyboard(List<TargetChannel> channels) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (TargetChannel ch : channels) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("📢 " + ch.getTitle());
            btn.setCallbackData("target_" + ch.getId());
            row.add(btn);
            rows.add(row);
        }
        markup.setKeyboard(rows);
        return markup;
    }

    // ✅ ИСПРАВЛЕННЫЙ МЕТОД! ← ТОЛЬКО ЗДЕСЬ ИЗМЕНЕНИЕ
    public InlineKeyboardMarkup getTargetChannelsListKeyboard(List<TargetChannel> channels) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (TargetChannel ch : channels) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            // КНОПКА КАНАЛА
            InlineKeyboardButton channelBtn = new InlineKeyboardButton();
            channelBtn.setText("📢 " + ch.getTitle());channelBtn.setCallbackData("mychannel_" + ch.getId());
            row.add(channelBtn);

            // ✅ НОВАЯ КНОПКА 🗑 УДАЛИТЬ
            InlineKeyboardButton deleteBtn = new InlineKeyboardButton();
            deleteBtn.setText("🗑");
            deleteBtn.setCallbackData("delete_channel_" + ch.getId());
            row.add(deleteBtn);

            rows.add(row);
        }

        // Кнопка "Назад к списку каналов" (если нужно)
        markup.setKeyboard(rows);
        return markup;
    }

    // МЕНЮ УПРАВЛЕНИЯ КАНАЛОМ - БЕЗ ИЗМЕНЕНИЙ
    public InlineKeyboardMarkup getTargetChannelMenu(Long targetId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton sourcesBtn = new InlineKeyboardButton();
        sourcesBtn.setText("📋 Источники");
        sourcesBtn.setCallbackData("channel_sources_" + targetId);
        row1.add(sourcesBtn);
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("🔙 Назад к списку каналов");
        backBtn.setCallbackData("back_to_channels");
        row2.add(backBtn);
        rows.add(row2);

        markup.setKeyboard(rows);
        return markup;
    }
}