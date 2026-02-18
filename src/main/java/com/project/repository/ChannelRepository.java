package com.project.repository;

import com.project.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Channel findByName(String name);
    Optional<Channel> findByChannelId(String channelId); //
}