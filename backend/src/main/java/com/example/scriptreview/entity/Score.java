package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "score")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    @Column(name = "judge_id", nullable = false)
    private Long judgeId;

    @Column(name = "creativity_score", nullable = false)
    private Integer creativityScore;

    @Column(name = "commercial_score", nullable = false)
    private Integer commercialScore;

    @Column(name = "difficulty_score", nullable = false)
    private Integer difficultyScore;

    @Column(name = "logic_score", nullable = false)
    private Integer logicScore;

    @Column(name = "audience_score", nullable = false)
    private Integer audienceScore;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "is_extreme")
    @Builder.Default
    private Boolean isExtreme = false;

    @Column(name = "valid")
    @Builder.Default
    private Boolean valid = true;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    @Column(name = "invalidated_reason", length = 255)
    private String invalidatedReason;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}