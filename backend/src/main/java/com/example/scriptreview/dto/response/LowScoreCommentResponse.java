package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowScoreCommentResponse {

    private Long scriptId;
    private String scriptTitle;
    private Double totalScore;
    private String grade;
    private List<CommentDetail> comments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentDetail {
        private String judgeName;
        private String comment;
        private String createdAt;
    }
}