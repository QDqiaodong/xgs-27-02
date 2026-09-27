package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "script")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "genre", nullable = false, length = 50)
    private String genre;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    @Column(name = "target_audience", nullable = false, length = 100)
    private String targetAudience;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "author", length = 100)
    private String author;

    @Column(name = "source", length = 200)
    private String source;

    /** 投稿联系方式（电话/邮箱/社交账号等）。原稿字段，评委与查看员的任何响应都不得携带。 */
    @Column(name = "contact_info", columnDefinition = "TEXT")
    private String contactInfo;

    /** 附件说明。原稿字段，脱敏视图按同一套代号规则派生。 */
    @Column(name = "attachment_note", columnDefinition = "TEXT")
    private String attachmentNote;

    /**
     * 本脚本脱敏代号随机盐：代号在脚本内稳定，不同脚本使用不同盐值，
     * 同一投稿人在不同脚本中得到不可横向关联的代号。
     */
    @Column(name = "mask_salt", nullable = false, length = 16)
    private String maskSalt;

    /** 最近一次身份识别对应的原稿指纹；原稿未变更时复用既有代号映射，变更后自动重新识别。 */
    @Column(name = "analyze_version", length = 64)
    private String analyzeVersion;

    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "grade", length = 10)
    private String grade;

    @Column(name = "total_score")
    private Double totalScore;

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