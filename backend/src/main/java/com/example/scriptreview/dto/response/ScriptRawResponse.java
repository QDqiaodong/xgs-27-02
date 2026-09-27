package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员受控原稿视图：完整保留作者、来源机构、联系方式与正文原文。
 * 仅 /api/scripts/{id}/raw 受控入口（ADMIN）返回，每次查看均写入脱敏审计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptRawResponse {

    private Long id;
    private String title;
    private String genre;
    private Integer duration;
    private String targetAudience;

    /** ===== 以下为原稿区域，仅管理员受控可见 ===== */
    private String author;
    private String source;
    private String contactInfo;
    private String description;
    private String attachmentNote;

    private String status;
    private String grade;
    private Double totalScore;
    private String createdAt;
    private String updatedAt;
}
