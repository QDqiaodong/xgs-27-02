package com.example.scriptreview.service;

import com.example.scriptreview.entity.Script;
import com.example.scriptreview.entity.ScriptAssignment;
import com.example.scriptreview.repository.ScoreRepository;
import com.example.scriptreview.repository.ScriptAssignmentRepository;
import com.example.scriptreview.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 脚本状态统一流转。所有改变评分/分配/回避结果的入口都必须在持有脚本行锁后调用，
 * 保证“页面显示已换人、总分却混入旧评分”这类不一致不会出现。
 *
 * 状态：PENDING 待评审 / REVIEWING 评审中 / PENDING_SUPPLEMENT 待补评 / GRADED 已分级
 */
@Service
@RequiredArgsConstructor
public class ScriptStatusService {

    /** 分级所需的最低有效评分人数 */
    public static final int REQUIRED_VALID_SCORES = 2;

    private final ScriptRepository scriptRepository;
    private final ScriptAssignmentRepository assignmentRepository;
    private final ScoreRepository scoreRepository;

    /**
     * 依据“当时有效评分 + 未完成分配”重算脚本状态。调用方必须先以 FOR UPDATE 锁定脚本行。
     */
    @Transactional
    public String recomputeStatus(Long scriptId) {
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new RuntimeException("脚本不存在"));

        List<ScriptAssignment> assignments = assignmentRepository.findByScriptId(scriptId);
        long validScoreCount = scoreRepository.countByScriptIdAndValidTrue(scriptId);
        long openAssigned = assignments.stream()
                .filter(a -> "ASSIGNED".equals(a.getStatus()))
                .count();
        boolean hadRecusal = assignments.stream()
                .anyMatch(a -> "RECUSED".equals(a.getStatus()));

        if (validScoreCount >= REQUIRED_VALID_SCORES) {
            // 有效评分已达分级人数要求，等待分级
            script.setStatus("REVIEWING");
        } else if (hadRecusal) {
            // 因回避导致有效评分不足：已撤销等级/等待替补补评
            script.setStatus("PENDING_SUPPLEMENT");
        } else if (openAssigned > 0) {
            script.setStatus("REVIEWING");
        } else {
            // 从未进入评审流程的脚本保持待评审
            script.setStatus("PENDING");
        }
        scriptRepository.save(script);
        return script.getStatus();
    }
}
