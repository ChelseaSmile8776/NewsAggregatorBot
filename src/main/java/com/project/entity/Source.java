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

    private String url; // https://t.me/s/durov
    private String name;

    @Column(length = 2000)
    private String systemPrompt; // "Ты крипто-эксперт..."

    @ManyToOne
    @JoinColumn(name = "target_channel_id")
    private TargetChannel targetChannel;

    // --- НОВОЕ ПОЛЕ ---
    private Integer lastPostId = 0;
}
