package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptResponse {

    private Long id;
    private String title;
    private String genre;
    private Integer duration;
    private String targetAudience;
    private String description;
    private String author;
    private String source;
    private String status;
    private String grade;
    private Double totalScore;
    private Long validScoreCount;
    private String createdAt;
    private String updatedAt;
}
