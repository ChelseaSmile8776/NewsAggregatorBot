package com.project.config;

import com.project.bot.NewsBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
public class BotInitializer {

    @Bean
    public TelegramBotsApi telegramBotsApi(NewsBot newsBot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        try {
            api.registerBot(newsBot);
            log.info("Telegram Bot registered successfully!");
        } catch (TelegramApiException e) {
            log.error("Failed to register bot: " + e.getMessage());
        }
        return api;
    }
}
