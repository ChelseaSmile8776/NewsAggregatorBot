package com.project.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "sources")
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url; // Ссылка на источник (t.me/durov)

    private String name; // Название источника

    @Column(length = 5000) // Увеличим длину, чтобы влезали сложные промпты
    private String systemPrompt = "Ты — опытный редактор новостей. Твоя задача — проанализировать текст, выделить главную суть и написать краткую выжимку (2-3 предложения). Стиль: информативный, нейтральный. Используй эмодзи по теме. В конце добавь хештеги.";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_channel_id")
    private TargetChannel targetChannel; // К какому твоему каналу привязан этот источник
}
