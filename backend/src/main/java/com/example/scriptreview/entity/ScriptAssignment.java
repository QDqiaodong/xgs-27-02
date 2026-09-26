package com.example.scriptreview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "script_assignment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    @Column(name = "judge_id", nullable = false)
    private Long judgeId;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(length = 20)
    @Builder.Default
    private String status = "ASSIGNED";

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "assigned_at")
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "recused_at")
    private LocalDateTime recusedAt;

    @Column(name = "recusal_application_id")
    private Long recusalApplicationId;

    @Column(name = "replaces_assignment_id")
    private Long replacesAssignmentId;
}
