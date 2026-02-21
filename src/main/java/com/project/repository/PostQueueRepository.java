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

    // ✅ НОВЫЙ МЕТОД ДЛЯ УДАЛЕНИЯ!
    @Modifying
    @Query("DELETE FROM PostQueue p WHERE p.targetChannel.id = :targetId")
    void deleteByTargetChannelId(@Param("targetId") Long targetId);

    List<PostQueue> findByStatusAndImageUrlContainingIgnoreCaseOrderByScheduledTimeAsc(
            PostQueue.Status status, String imageUrl);
}
