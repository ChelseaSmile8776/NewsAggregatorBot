package com.project.service.keyboard;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Service
public class KeyboardService {

    public ReplyKeyboardMarkup getMainMenu() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true); // Кнопки компактные
        markup.setSelective(true);
        markup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        // 1 ряд
        KeyboardRow row1 = new KeyboardRow();
        row1.add("📺 Мои Каналы");
        row1.add("📢 Сделать Пост");
        keyboard.add(row1);

        // 2 ряд
        KeyboardRow row2 = new KeyboardRow();
        row2.add("👥 Пользователи");
        row2.add("⚙️ Настройки");
        keyboard.add(row2);

        markup.setKeyboard(keyboard);
        return markup;
    }
}
