package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 替补评委候选：必须未参与该脚本、账号状态正常、专业方向与题材匹配。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubstituteCandidateResponse {

    private Long judgeId;
    private String judgeName;
    private String expertise;
    private Boolean expertiseMatched;
}
