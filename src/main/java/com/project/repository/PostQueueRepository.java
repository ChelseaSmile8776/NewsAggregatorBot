package com.project.repository;

import com.project.entity.PostQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface PostQueueRepository extends JpaRepository<PostQueue, Long> {
    // Найти готовые к публикации посты (время пришло + статус PENDING), отсортировать по важности
    @Query("SELECT p FROM PostQueue p WHERE p.status = 'PENDING' AND p.scheduledTime <= :now ORDER BY p.priority DESC, p.scheduledTime ASC")
    List<PostQueue> findReadyToPublish(LocalDateTime now, Pageable pageable);
}
