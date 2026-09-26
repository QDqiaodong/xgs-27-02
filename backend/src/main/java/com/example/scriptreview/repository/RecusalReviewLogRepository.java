package com.example.scriptreview.repository;

import com.example.scriptreview.entity.RecusalReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecusalReviewLogRepository extends JpaRepository<RecusalReviewLog, Long> {

    List<RecusalReviewLog> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
}
