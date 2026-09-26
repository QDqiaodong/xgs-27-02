package com.example.scriptreview.repository;

import com.example.scriptreview.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    Page<AuditLog> findByModule(String module, Pageable pageable);
    
    Page<AuditLog> findByModuleAndAction(String module, String action, Pageable pageable);
    
    Page<AuditLog> findBySuccess(Boolean success, Pageable pageable);
}