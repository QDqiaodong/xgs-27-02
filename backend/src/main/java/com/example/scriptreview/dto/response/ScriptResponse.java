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
    private String attachmentNote;
    private String author;
    private String source;
    /** true 表示该响应已经过双盲脱敏（评委/查看员视角）；false 表示管理员看到的原稿 */
    private Boolean masked;
    /** 待管理员核对的模糊线索数 */
    private Integer pendingClueCount;
    private String status;
    private String grade;
    private Double totalScore;
    private Long validScoreCount;
    private String createdAt;
    private String updatedAt;
}
