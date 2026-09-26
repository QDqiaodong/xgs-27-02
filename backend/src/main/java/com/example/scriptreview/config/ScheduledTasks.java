package com.example.scriptreview.config;

import com.example.scriptreview.entity.Script;
import com.example.scriptreview.repository.ScoreRepository;
import com.example.scriptreview.repository.ScriptAssignmentRepository;
import com.example.scriptreview.repository.ScriptRepository;
import com.example.scriptreview.service.GradeService;
import com.example.scriptreview.service.ScriptStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final ScriptRepository scriptRepository;
    private final ScriptAssignmentRepository assignmentRepository;
    private final ScoreRepository scoreRepository;
    private final GradeService gradeService;

    @Scheduled(fixedRate = 300000)
    public void autoCalculateGrade() {
        // 只统计当前有效评分：已回避评委的失效评分不参与，人数不足不会自动分级
        List<Script> reviewingScripts = scriptRepository.findByStatus("REVIEWING");

        for (Script script : reviewingScripts) {
            long validCount = scoreRepository.countByScriptIdAndValidTrue(script.getId());

            if (validCount >= ScriptStatusService.REQUIRED_VALID_SCORES) {
                try {
                    gradeService.calculateGrade(script.getId());
                    log.info("自动计算分级完成: 脚本ID={}, 标题={}, 有效评分数={}",
                            script.getId(), script.getTitle(), validCount);
                } catch (Exception e) {
                    log.error("自动计算分级失败: 脚本ID={}, 错误={}", script.getId(), e.getMessage());
                }
            }
        }
    }

    @Scheduled(fixedRate = 600000)
    public void checkDeadline() {
        List<com.example.scriptreview.entity.ScriptAssignment> overdueAssignments =
                assignmentRepository.findByStatusAndDeadlineBefore("ASSIGNED", LocalDateTime.now());

        for (com.example.scriptreview.entity.ScriptAssignment assignment : overdueAssignments) {
            log.warn("评分任务即将到期: 脚本ID={}, 评委ID={}, 截止时间={}",
                assignment.getScriptId(), assignment.getJudgeId(), assignment.getDeadline());
        }
    }
}
