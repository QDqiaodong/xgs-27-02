package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 脚本脱敏身份：同一脚本内指向同一投稿身份的若干线索共用一个稳定代号。
 * 代号按脚本维度生成（哈希盐 + 脚本ID + 身份ID），同一投稿人在不同脚本中
 * 会得到互不相同的代号，评委无法据此横向关联。
 */
@Entity
@Table(name = "script_identity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    @Column(name = "alias_code", nullable = false, length = 100)
    private String aliasCode;

    @Column(name = "kind", nullable = false, length = 20)
    @Builder.Default
    private String kind = "RELATED"; // SUBMITTER-投稿人本人 / RELATED-其他相关身份

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
