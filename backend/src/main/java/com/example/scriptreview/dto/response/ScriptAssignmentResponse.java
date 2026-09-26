package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptAssignmentResponse {

    private Long id;
    private Long scriptId;
    private String scriptTitle;
    private Long judgeId;
    private String judgeName;
    private Long assignedBy;
    private String assignedByName;
    private String status;
    private String deadline;
    private String assignedAt;
    private String recusedAt;
    private Long recusalApplicationId;
    private Long replacesAssignmentId;
}
