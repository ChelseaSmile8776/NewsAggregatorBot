package com.project.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "channels")
public class Channel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String channelId;

    private String name;

    private String username; // <-- ДОБАВЬ ЭТО ПОЛЕ!

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    private String signature;
}
