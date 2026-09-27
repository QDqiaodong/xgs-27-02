package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 脚本内的身份分组：同一投稿身份（一个姓名 / 一个号码 / 一个机构 / 一个账号）
 * 在该脚本的全部线索共用一个分组与一个稳定代号。
 * 分组严格隶属于单个脚本，跨脚本不复用，代号因此无法被评委横向关联。
 */
@Entity
@Table(name = "identity_group",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_script_key",
                columnNames = {"script_id", "cluster_key"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    /** 脚本内稳定归并键：线索类型 + 归一化原文。不同姓名即使相似也不会归并。 */
    @Column(name = "cluster_key", nullable = false, length = 120)
    private String clusterKey;

    /** NAME / PHONE / EMAIL / ACCOUNT / ORGANIZATION */
    @Column(name = "clue_type", nullable = false, length = 30)
    private String clueType;

    /** 脚本内稳定代号字母 A/B/C…，按首次识别顺序分配，刷新与重新登录保持一致。 */
    @Column(name = "label_letter", nullable = false, length = 8)
    private String labelLetter;

    /** PENDING 待管理员核对 / CONFIRMED_IDENTITY 确认投稿身份 / CONFIRMED_CHARACTER 确认剧情人物。 */
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "representative", nullable = false, length = 255)
    private String representative;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_by_name", length = 100)
    private String decidedByName;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
