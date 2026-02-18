package com.project.repository;

import com.project.entity.TargetChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TargetChannelRepository extends JpaRepository<TargetChannel, Long> {
    Optional<TargetChannel> findByTelegramId(String telegramId);
}
