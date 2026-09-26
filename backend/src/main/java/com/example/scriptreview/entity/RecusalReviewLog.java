package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 回避申请处理轨迹：记录每一次操作的操作者、前后状态与理由，供审计追溯。
 * action: CREATE / SUPPLEMENT / REQUEST_SUPPLEMENT / APPROVE / REJECT / SUBSTITUTE
 */
@Entity
@Table(name = "recusal_review_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecusalReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 100)
    private String operatorName;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
