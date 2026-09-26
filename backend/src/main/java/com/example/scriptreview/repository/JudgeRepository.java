package com.example.scriptreview.repository;

import com.example.scriptreview.entity.Judge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JudgeRepository extends JpaRepository<Judge, Long> {

    List<Judge> findByRole(String role);

    Optional<Judge> findByUserId(Long userId);
}
