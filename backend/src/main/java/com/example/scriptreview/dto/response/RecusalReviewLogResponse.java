package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecusalReviewLogResponse {

    private Long id;
    private Long applicationId;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String fromStatus;
    private String toStatus;
    private String comment;
    private String createdAt;
}
