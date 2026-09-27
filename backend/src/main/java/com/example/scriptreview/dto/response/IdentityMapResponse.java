package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理员代号映射核对视图，明确区分三个区域：
 * 1) raw：原始内容（原稿）；
 * 2) groups/clues：识别出的线索与身份分组（含识别依据、状态与代号）；
 * 3) masked：脱敏结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityMapResponse {

    private Long scriptId;
    private String scriptSalt;

    private RawSection raw;
    private List<GroupView> groups;
    private MaskedSection masked;
    private List<AuditView> recentAudits;
    private Integer pendingCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RawSection {
        private String title;
        private String author;
        private String source;
        private String contactInfo;
        private String description;
        private String attachmentNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupView {
        private Long id;
        private String clueType;
        private String clueTypeLabel;
        private String labelLetter;
        /** 该身份在脱敏视图中使用的代号（剧情人物为“保留原文”标记）。 */
        private String code;
        private String status;
        private String statusLabel;
        private String representative;
        private String detectReason;
        private String detectReasonLabel;
        private Integer clueCount;
        private List<ClueView> clues;
        private String decidedByName;
        private String decidedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
@AllArgsConstructor
    public static class ClueView {
        private Long id;
        private Long groupId;
        private String fieldName;
        private String fieldLabel;
        private Integer startOffset;
        private Integer endOffset;
        private String matchedText;
        private String reason;
        private String reasonLabel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaskedSection {
        private String title;
        private String description;
        private String attachmentNote;
        /** 元数据身份字段在脱敏视图中的整体替换结果。 */
        private String authorCode;
        private String sourceCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditView {
        private Long id;
        private String action;
        private String actionLabel;
        private String viewerName;
        private String ipAddress;
        private String detail;
        private String createdAt;
    }
}
