package com.project.repository;

import com.project.entity.ProcessedNews;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedNewsRepository extends JpaRepository<ProcessedNews, String> {
}
