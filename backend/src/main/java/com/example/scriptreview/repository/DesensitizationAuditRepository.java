package com.example.scriptreview.repository;

import com.example.scriptreview.entity.DesensitizationAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DesensitizationAuditRepository extends JpaRepository<DesensitizationAudit, Long> {

    Page<DesensitizationAudit> findByScriptIdOrderByCreatedAtDescIdDesc(Long scriptId, Pageable pageable);

    Page<DesensitizationAudit> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
