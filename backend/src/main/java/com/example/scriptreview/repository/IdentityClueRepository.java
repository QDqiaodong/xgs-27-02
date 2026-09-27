package com.example.scriptreview.repository;

import com.example.scriptreview.entity.IdentityClue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface IdentityClueRepository extends JpaRepository<IdentityClue, Long> {

    List<IdentityClue> findByScriptIdOrderByOrdinalAsc(Long scriptId);

    List<IdentityClue> findByScriptIdAndFieldName(Long scriptId, String fieldName);

    List<IdentityClue> findByGroupId(Long groupId);

    @Transactional
    void deleteByScriptId(Long scriptId);
}
