package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dimension_weight")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dimension_name", nullable = false, length = 50)
    private String dimensionName;

    @Column(name = "weight", nullable = false)
    private Double weight;

    @Column(name = "description", length = 200)
    private String description;
}