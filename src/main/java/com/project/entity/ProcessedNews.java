package com.project.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "processed_news")
public class ProcessedNews {
    @Id
    private String urlHash;

    private String originalUrl;

    private LocalDateTime createdAt = LocalDateTime.now();
}
