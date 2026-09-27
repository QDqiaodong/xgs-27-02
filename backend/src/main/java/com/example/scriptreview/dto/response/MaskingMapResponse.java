package com.example.scriptreview.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理员专用的脚本脱敏映射视图：明确区分原稿、识别出的线索与脱敏结果。
 */
@Data
public class MaskingMapResponse {

    private Long scriptId;
    /** 原稿内容（仅管理员可见） */
    private MaskedFields original;
    /** 评委/查看员实际看到的脱敏结果 */
    private MaskedFields masked;
    /** 本脚本内的全部身份及稳定代号 */
    private List<IdentityItem> identities;
    /** 识别出的全部线索及其核对状态 */
    private List<ClueItem> clues;
    /** 待管理员核对的线索数 */
    private Integer pendingCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaskedFields {
        private String title;
        private String author;
        private String source;
        private String description;
        private String attachmentNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdentityItem {
        private Long id;
        private String aliasCode;
        private String kind; // SUBMITTER/RELATED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClueItem {
        private Long id;
        private String clueType; // NAME/PHONE/EMAIL/ORG/SOCIAL
        private String fieldOrigin; // AUTHOR/SOURCE/TITLE/DESCRIPTION/ATTACHMENT
        private String originalText;
        private String status; // CONFIRMED/PENDING/CHARACTER
        private Long identityId;
        private String aliasCode;
        private String confirmedByName;
        private String confirmedAt;
    }
}
