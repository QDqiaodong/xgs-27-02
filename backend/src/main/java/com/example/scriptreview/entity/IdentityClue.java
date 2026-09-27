package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 身份线索：脱敏引擎在元数据或正文中识别到的单处可定位投稿人的片段，
 * 带所属字段与精确偏移，替换时按区间整体改写而不是“遮住一半”。
 */
@Entity
@Table(name = "identity_clue",
        uniqueConstraints = @UniqueConstraint(name = "uk_clue_span",
                columnNames = {"script_id", "field_name", "start_offset", "end_offset"}))
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

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** AUTHOR / SOURCE / CONTACT_INFO / TITLE / DESCRIPTION / ATTACHMENT_NOTE */
    @Column(name = "field_name", nullable = false, length = 30)
    private String fieldName;

    @Column(name = "start_offset", nullable = false)
    private Integer startOffset;

    /** 结束下标（不含） */
    @Column(name = "end_offset", nullable = false)
    private Integer endOffset;

    @Column(name = "matched_text", nullable = false, length = 255)
    private String matchedText;

    /** METADATA / PATTERN / NAME_CONTEXT / NAME_BARE */
    @Column(name = "detect_reason", nullable = false, length = 60)
    private String detectReason;

    /** 全脚本扫描顺序，决定代号字母分配，保证代号稳定可复现。 */
    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
