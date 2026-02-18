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

    @ManyToOne
    private Channel channel;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private int priority; // 0 - обычный, 100 - реклама

    private LocalDateTime scheduledTime;

    @Enumerated(EnumType.STRING)
    private Status status;

    public enum Status { PENDING, PUBLISHED, ERROR }
}
