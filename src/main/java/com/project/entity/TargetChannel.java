package com.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "target_channels")
public class TargetChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String telegramId; // ID твоего канала (например, -1001234567890)

    private String title; // Название (для тебя, чтобы не запутаться)

    @OneToMany(mappedBy = "targetChannel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Source> sources = new ArrayList<>(); // Список источников для этого канала
}
