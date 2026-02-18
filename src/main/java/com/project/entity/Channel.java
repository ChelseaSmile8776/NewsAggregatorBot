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
    private String channelId; // ID канала (-100...)

    private String name;      // Название для себя (Tech, Crypto)

    @Column(columnDefinition = "TEXT")
    private String systemPrompt; // "Ты дерзкий криптан..."

    private String signature; // Подпись
}
