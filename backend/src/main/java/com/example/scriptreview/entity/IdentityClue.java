package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 身份线索：从脚本元数据或正文中识别出的、可能直接指向投稿人的片段。
 * 状态机：
 *   CONFIRMED —— 确认属于投稿身份，脱敏视图中替换为所属身份的稳定代号；
 *   PENDING   —— 无法确定是否为身份线索，核对完成前一律以更保守的占位符隐藏；
 *   CHARACTER —— 管理员确认属于正文剧情人物，脱敏视图中保留原文。
 * 确认结果只影响脱敏视图与映射，绝不改写原稿。
 */
@Entity
@Table(name = "identity_clue")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityClue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    @Column(name = "identity_id")
    private Long identityId;

    @Column(name = "clue_type", nullable = false, length = 20)
    private String clueType; // NAME/PHONE/EMAIL/ORG/SOCIAL

    @Column(name = "field_origin", nullable = false, length = 20)
    private String fieldOrigin; // AUTHOR/SOURCE/TITLE/DESCRIPTION/ATTACHMENT

    @Column(name = "original_text", nullable = false, length = 191)
    private String originalText;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // CONFIRMED/PENDING/CHARACTER

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_by_name", length = 100)
    private String confirmedByName;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
