package com.example.scriptreview.repository;

import com.example.scriptreview.entity.ScriptIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptIdentityRepository extends JpaRepository<ScriptIdentity, Long> {

    List<ScriptIdentity> findByScriptId(Long scriptId);

    Optional<ScriptIdentity> findByScriptIdAndKind(Long scriptId, String kind);

    long countByScriptId(Long scriptId);

    void deleteByScriptId(Long scriptId);
}
