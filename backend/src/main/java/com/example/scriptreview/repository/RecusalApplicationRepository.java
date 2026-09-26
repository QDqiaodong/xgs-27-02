package com.example.scriptreview.repository;

import com.example.scriptreview.entity.RecusalApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecusalApplicationRepository extends JpaRepository<RecusalApplication, Long> {

    List<RecusalApplication> findByScriptId(Long scriptId);

    List<RecusalApplication> findByJudgeId(Long judgeId);

    List<RecusalApplication> findByStatus(String status);

    List<RecusalApplication> findByScriptIdAndJudgeId(Long scriptId, Long judgeId);

    List<RecusalApplication> findByScriptIdAndJudgeIdOrderByCreatedAtDesc(Long scriptId, Long judgeId);

    Optional<RecusalApplication> findByAssignmentId(Long assignmentId);

    /**
     * 悲观锁加载申请：管理员处理前锁定该行，配合 @Version 乐观锁，
     * 保证两名管理员同时处理时只有一个最终决定。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecusalApplication r where r.id = :id")
    Optional<RecusalApplication> findByIdForUpdate(@Param("id") Long id);

    /**
     * 锁定同一脚本下的申请记录集（含间隙锁）：并发申报时串行化重复申报检查，
     * 保证同一评委在同一脚本上不会出现两条同时存在的未结束申请。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecusalApplication r where r.scriptId = :scriptId")
    List<RecusalApplication> findByScriptIdForUpdate(@Param("scriptId") Long scriptId);
}
