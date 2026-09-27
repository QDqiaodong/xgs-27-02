package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 脚本脱敏视图响应：评委、查看员以及管理员的常规页面都只拿到这份从原稿派生的内容。
 * 该对象按设计不包含 author/source/contactInfo/attachmentNote 等原始身份字段，
 * 从响应结构上保证任何评委可到达的入口都无法返回原文。
 */
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
    /** 附件说明的脱敏结果（原稿字段经同一套代号规则派生）。 */
    private String attachmentNote;
    private String status;
    private String grade;
    private Double totalScore;
    private Long validScoreCount;

    /** 当前响应是否为脱敏视图（管理员常规页面同样为 true，原稿仅能通过受控入口查看）。 */
    private Boolean masked;
    /** 待管理员核对的可疑身份数量；核对完成前评委侧看到的是更保守的隐藏结果。 */
    private Integer pendingCount;
    private String createdAt;
    private String updatedAt;
}
