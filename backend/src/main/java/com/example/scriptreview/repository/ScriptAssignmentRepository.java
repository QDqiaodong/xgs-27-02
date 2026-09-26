package com.example.scriptreview.repository;

import com.example.scriptreview.entity.ScriptAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptAssignmentRepository extends JpaRepository<ScriptAssignment, Long> {
    List<ScriptAssignment> findByScriptId(Long scriptId);
    List<ScriptAssignment> findByStatusAndDeadlineBefore(String status, java.time.LocalDateTime deadline);
    List<ScriptAssignment> findByJudgeId(Long judgeId);
    Optional<ScriptAssignment> findByScriptIdAndJudgeId(Long scriptId, Long judgeId);
    List<ScriptAssignment> findByJudgeIdAndStatus(Long judgeId, String status);
    long countByScriptIdAndStatus(Long scriptId, String status);
    Optional<ScriptAssignment> findByReplacesAssignmentId(Long replacesAssignmentId);

    /** 行锁：回避批准终止分配、替补接管、分级并发时串行化对同一脚本分配集的修改 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ScriptAssignment a where a.scriptId = :scriptId")
    List<ScriptAssignment> findByScriptIdForUpdate(@Param("scriptId") Long scriptId);
}
