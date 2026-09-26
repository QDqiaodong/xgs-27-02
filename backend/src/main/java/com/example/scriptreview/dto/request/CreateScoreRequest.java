package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScoreRequest {

    @NotNull(message = "脚本ID不能为空")
    private Long scriptId;

    @NotNull(message = "评委ID不能为空")
    private Long judgeId;

    @NotNull(message = "创意评分不能为空")
    @Min(value = 1, message = "创意评分范围为1-10")
    @Max(value = 10, message = "创意评分范围为1-10")
    private Integer creativityScore;

    @NotNull(message = "商业潜力评分不能为空")
    @Min(value = 1, message = "商业潜力评分范围为1-10")
    @Max(value = 10, message = "商业潜力评分范围为1-10")
    private Integer commercialScore;

    @NotNull(message = "落地难度评分不能为空")
    @Min(value = 1, message = "落地难度评分范围为1-10")
    @Max(value = 10, message = "落地难度评分范围为1-10")
    private Integer difficultyScore;

    @NotNull(message = "逻辑完整度评分不能为空")
    @Min(value = 1, message = "逻辑完整度评分范围为1-10")
    @Max(value = 10, message = "逻辑完整度评分范围为1-10")
    private Integer logicScore;

    @NotNull(message = "受众适配评分不能为空")
    @Min(value = 1, message = "受众适配评分范围为1-10")
    @Max(value = 10, message = "受众适配评分范围为1-10")
    private Integer audienceScore;

    @Size(max = 2000, message = "评审意见不能超过2000个字符")
    private String comment;
}