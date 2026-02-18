package com.project.repository;

import com.project.entity.ProcessedNews;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedNewsRepository extends JpaRepository<ProcessedNews, String> {
}
