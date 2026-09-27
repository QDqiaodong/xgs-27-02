package com.example.scriptreview.repository;

import com.example.scriptreview.entity.IdentityClue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IdentityClueRepository extends JpaRepository<IdentityClue, Long> {

    List<IdentityClue> findByScriptId(Long scriptId);

    boolean existsByScriptId(Long scriptId);

    long countByScriptIdAndStatus(Long scriptId, String status);

    Optional<IdentityClue> findByScriptIdAndFieldOriginAndClueTypeAndOriginalText(
            Long scriptId, String fieldOrigin, String clueType, String originalText);

    void deleteByScriptId(Long scriptId);
}
