package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评委利益冲突回避申请。
 * status: PENDING 待处理 / SUPPLEMENT_REQUESTED 待补充 / APPROVED 已批准 / REJECTED 已驳回
 * type: AUTHOR 与作者 / ORGANIZATION 与来源机构 / TOPIC 与题材 / OTHER 其他
 */
@Entity
@Table(name = "recusal_application")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecusalApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    @Column(name = "judge_id", nullable = false)
    private Long judgeId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "evidence", nullable = false, columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    @Column(name = "supplement_info", columnDefinition = "TEXT")
    private String supplementInfo;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_by_name", length = 100)
    private String decidedByName;

    @Column(name = "decision_comment", columnDefinition = "TEXT")
    private String decisionComment;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** 乐观锁：同一条申请被两名管理员并发处理时，只有一个决定能落库 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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
