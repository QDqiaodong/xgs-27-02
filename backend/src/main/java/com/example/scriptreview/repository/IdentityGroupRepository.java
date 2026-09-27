package com.example.scriptreview.repository;

import com.example.scriptreview.entity.IdentityGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IdentityGroupRepository extends JpaRepository<IdentityGroup, Long> {

    List<IdentityGroup> findByScriptIdOrderByLabelLetterAscIdAsc(Long scriptId);

    Optional<IdentityGroup> findByScriptIdAndClusterKey(Long scriptId, String clusterKey);

    /** 核对分组结论时加行锁，两名管理员并发核对只有一个最终结论。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from IdentityGroup g where g.id = :id")
    Optional<IdentityGroup> findByIdForUpdate(@Param("id") Long id);

    long countByScriptIdAndStatus(Long scriptId, String status);
}
