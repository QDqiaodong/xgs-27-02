package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回避申请响应。
 * canViewSensitive=false 时（普通查看员/非本人评委），reason/evidence/supplementInfo/decisionComment 不下发，
 * 调用方只能看到回避类型与处理状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecusalApplicationResponse {

    private Long id;
    private Long scriptId;
    private String scriptTitle;
    private Long judgeId;
    private String judgeName;
    private Long assignmentId;
    private String type;
    private String status;
    private Long submittedBy;
    private String submittedByName;
    private String submittedByRole;
    private Boolean submittedOnBehalf;
    private Long decidedBy;
    private String decidedByName;
    private String decidedAt;
    private String createdAt;
    private String updatedAt;

    private String reason;
    private String evidence;
    private String supplementInfo;
    private String decisionComment;

    private Boolean canViewSensitive;

    private Long substituteAssignmentId;
    private Long substituteJudgeId;
    private String substituteJudgeName;
}
