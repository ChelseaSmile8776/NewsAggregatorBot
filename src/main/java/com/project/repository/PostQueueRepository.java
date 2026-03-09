package com.project.repository;

import com.project.entity.PostQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface PostQueueRepository extends JpaRepository<PostQueue, Long> {

    @Query("SELECT p FROM PostQueue p WHERE p.status = 'PENDING' AND p.scheduledTime <= :now ORDER BY p.priority DESC, p.scheduledTime ASC")
    List<PostQueue> findReadyToPublish(LocalDateTime now, Pageable pageable);

    List<PostQueue> findAllByStatusAndScheduledTimeBefore(PostQueue.Status status, LocalDateTime time);
    List<PostQueue> findByStatusOrderByScheduledTimeAsc(PostQueue.Status status);

    @Modifying
    @Query("DELETE FROM PostQueue p WHERE p.targetChannel.id = :targetId")
    void deleteByTargetChannelId(@Param("targetId") Long targetId);

    List<PostQueue> findByStatusAndImageUrlContainingIgnoreCaseOrderByScheduledTimeAsc(
            PostQueue.Status status, String imageUrl);

    // ✅ Для дедупликации: последние отправленные посты за N часов
    @Query("SELECT p FROM PostQueue p WHERE p.status = 'SENT' AND p.scheduledTime >= :since ORDER BY p.scheduledTime DESC")
    List<PostQueue> findRecentSent(@Param("since") LocalDateTime since);
}
