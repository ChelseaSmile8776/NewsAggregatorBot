package com.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "post_queue")
public class PostQueue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10000)
    private String content;

    // ССЫЛКА НА ЦЕЛЕВОЙ КАНАЛ (Куда отправлять)
    @ManyToOne
    @JoinColumn(name = "target_channel_id")
    private TargetChannel targetChannel;

    private LocalDateTime scheduledTime;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int priority = 0;

    public enum Status { PENDING, SENT, ERROR }
}
