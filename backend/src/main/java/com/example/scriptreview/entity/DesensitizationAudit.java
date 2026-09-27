package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 脱敏查看审计：记录哪位管理员在何时查看了原稿 / 代号映射，
 * 以及对哪个可疑身份分组做出了什么核对结论。
 */
@Entity
@Table(name = "desensitization_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesensitizationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    /** VIEW_RAW / VIEW_MAP / DECIDE_GROUP */
    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "viewer_id")
    private Long viewerId;

    @Column(name = "viewer_name", length = 100)
    private String viewerName;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
