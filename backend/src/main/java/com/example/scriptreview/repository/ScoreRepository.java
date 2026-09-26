package com.example.scriptreview.repository;

import com.example.scriptreview.entity.Score;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScoreRepository extends JpaRepository<Score, Long> {

    List<Score> findByScriptId(Long scriptId);

    List<Score> findByJudgeId(Long judgeId);

    List<Score> findByScriptIdAndJudgeId(Long scriptId, Long judgeId);

    List<Score> findByScriptIdAndIsExtremeFalse(Long scriptId);

    List<Score> findByScriptIdAndValidTrue(Long scriptId);

    long countByScriptIdAndValidTrue(Long scriptId);

    Optional<Score> findByScriptIdAndJudgeIdAndValidTrue(Long scriptId, Long judgeId);

    /** 行锁：与回避审批/分级并发时，保证评分失效与分级计算串行化 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Score s where s.scriptId = :scriptId")
    List<Score> findByScriptIdForUpdate(@Param("scriptId") Long scriptId);

    void deleteByScriptId(Long scriptId);
}
