package com.project.repository;

import com.project.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SourceRepository extends JpaRepository<Source, Long> {
    Source findByName(String name);
    Optional<Source> findByChannelId(String channelId);
}
