package com.example.scriptreview.repository;

import com.example.scriptreview.entity.Script;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptRepository extends JpaRepository<Script, Long> {

    List<Script> findByStatus(String status);

    /**
     * 列表统一查询：状态、等级、题材三个条件任意组合（为 null 的条件不参与过滤），
     * 内容、总数、总页数都来自这一条查询，保证分页数据自洽。
     */
    @Query("SELECT s FROM Script s WHERE (:status IS NULL OR s.status = :status) " +
            "AND (:grade IS NULL OR s.grade = :grade) " +
            "AND (:genre IS NULL OR s.genre = :genre)")
    Page<Script> search(@Param("status") String status,
                        @Param("grade") String grade,
                        @Param("genre") String genre,
                        Pageable pageable);

    List<Script> findByGradeIn(List<String> grades);

    long countByGrade(String grade);

    long countByStatus(String status);

    /** 行锁：回避批准撤销等级、替补接管与分级并发时串行化脚本状态更新 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Script s where s.id = :id")
    Optional<Script> findByIdForUpdate(@Param("id") Long id);
}
