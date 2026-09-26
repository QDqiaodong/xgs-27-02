package com.example.scriptreview.repository;

import com.example.scriptreview.entity.DimensionWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DimensionWeightRepository extends JpaRepository<DimensionWeight, Long> {
    
    Optional<DimensionWeight> findByDimensionName(String dimensionName);
    
    List<DimensionWeight> findAll();
}