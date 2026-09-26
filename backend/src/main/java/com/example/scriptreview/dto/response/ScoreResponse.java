package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResponse {

    private Long id;
    private Long scriptId;
    private Long judgeId;
    private String judgeName;
    private Integer creativityScore;
    private Integer commercialScore;
    private Integer difficultyScore;
    private Integer logicScore;
    private Integer audienceScore;
    private String comment;
    private Boolean isExtreme;
    private Boolean valid;
    private String invalidatedAt;
    private String invalidatedReason;
    private String createdAt;
}
