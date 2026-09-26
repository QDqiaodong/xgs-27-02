package com.example.scriptreview.service;

import com.example.scriptreview.dto.request.CreateScoreRequest;
import com.example.scriptreview.dto.response.ScoreResponse;
import com.example.scriptreview.entity.Judge;
import com.example.scriptreview.entity.RecusalApplication;
import com.example.scriptreview.entity.Score;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.entity.ScriptAssignment;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.exception.ConflictException;
import com.example.scriptreview.repository.JudgeRepository;
import com.example.scriptreview.repository.RecusalApplicationRepository;
import com.example.scriptreview.repository.ScoreRepository;
import com.example.scriptreview.repository.ScriptAssignmentRepository;
import com.example.scriptreview.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> OPEN_RECUSAL = Arrays.asList("PENDING", "SUPPLEMENT_REQUESTED");

    private final ScoreRepository scoreRepository;
    private final JudgeRepository judgeRepository;
    private final ScriptRepository scriptRepository;
    private final ScriptAssignmentRepository assignmentRepository;
    private final RecusalApplicationRepository recusalRepository;
    private final ScriptStatusService scriptStatusService;

    @Transactional
    public ScoreResponse createScore(CreateScoreRequest request, User currentUser) {
        if (!Arrays.asList("ADMIN", "JUDGE").contains(currentUser.getRole())) {
            throw new AccessDeniedException("无权提交评分");
        }

        // 与回避审批/分级一致的加锁顺序：脚本 -> 分配 -> 评分
        Script script = scriptRepository.findByIdForUpdate(request.getScriptId())
                .orElseThrow(() -> new RuntimeException("脚本不存在"));
        Judge judge = judgeRepository.findById(request.getJudgeId())
                .orElseThrow(() -> new RuntimeException("评委不存在"));
        if (!"ACTIVE".equals(judge.getStatus())) {
            throw new RuntimeException("该评委账号已停用，不能提交评分");
        }

        // 评委只能为自己打分；管理员可代选评委（仍受下面全部约束限制）
        if ("JUDGE".equals(currentUser.getRole())) {
            Judge ownJudge = judgeRepository.findByUserId(currentUser.getId()).orElse(null);
            if (ownJudge == null || !ownJudge.getId().equals(judge.getId())) {
                throw new AccessDeniedException("评委只能提交本人的评分");
            }
        }

        ScriptAssignment assignment = assignmentRepository
                .findByScriptIdAndJudgeId(script.getId(), judge.getId())
                .orElseThrow(() -> new RuntimeException("该评委未被分配到此脚本，不能评分"));
        if (!"ASSIGNED".equals(assignment.getStatus())) {
            if ("RECUSED".equals(assignment.getStatus())) {
                throw new ConflictException("该评委已被批准回避此脚本，不能评分");
            }
            throw new ConflictException("该分配已完成评分，不能重复提交");
        }

        // 回避申请未处理时，脚本仍可查看，但该评委不能新提交评分
        List<RecusalApplication> recusals =
                recusalRepository.findByScriptIdAndJudgeId(script.getId(), judge.getId());
        for (RecusalApplication recusal : recusals) {
            if (OPEN_RECUSAL.contains(recusal.getStatus())) {
                throw new ConflictException("该评委存在未处理的回避申请，处理结束前不能提交评分");
            }
            if ("APPROVED".equals(recusal.getStatus())) {
                throw new ConflictException("该评委已被批准回避，不能评分");
            }
        }

        if (scoreRepository.findByScriptIdAndJudgeIdAndValidTrue(script.getId(), judge.getId()).isPresent()) {
            throw new ConflictException("该评委已对该脚本打分");
        }

        Score score = Score.builder()
                .scriptId(script.getId())
                .judgeId(judge.getId())
                .creativityScore(request.getCreativityScore())
                .commercialScore(request.getCommercialScore())
                .difficultyScore(request.getDifficultyScore())
                .logicScore(request.getLogicScore())
                .audienceScore(request.getAudienceScore())
                .comment(request.getComment())
                .isExtreme(false)
                .valid(true)
                .build();
        Score saved = scoreRepository.save(score);

        assignment.setStatus("COMPLETED");
        assignmentRepository.save(assignment);

        // 新评分进入后重算状态（第一次有效评分后仍保持评审中等待补评人数）
        scriptStatusService.recomputeStatus(script.getId());

        return toResponse(saved);
    }

    /**
     * 脚本评分列表：
     * - 管理员：全部（含已失效评分，保留原文供审计）
     * - 评委本人：有效评分 + 自己已失效的评分
     * - 其他评委/查看员：仅有效评分（失效旧评分从展示中排除）
     */
    @Transactional(readOnly = true)
    public List<ScoreResponse> getScoresByScriptId(Long scriptId, User currentUser) {
        Long currentJudgeId = "JUDGE".equals(currentUser.getRole())
                ? judgeRepository.findByUserId(currentUser.getId()).map(Judge::getId).orElse(null)
                : null;
        final Long selfJudgeId = currentJudgeId;
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());

        return scoreRepository.findByScriptId(scriptId).stream()
                .filter(score -> isAdmin
                        || Boolean.TRUE.equals(score.getValid())
                        || score.getJudgeId().equals(selfJudgeId))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** 按评委查询：管理员可查任意评委；评委只能查本人。 */
    @Transactional(readOnly = true)
    public List<ScoreResponse> getScoresByJudgeId(Long judgeId, User currentUser) {
        if (!"ADMIN".equals(currentUser.getRole())) {
            Judge ownJudge = judgeRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("当前账号未关联评委身份"));
            if (!ownJudge.getId().equals(judgeId)) {
                throw new AccessDeniedException("只能查看本人的评分记录");
            }
        }
        return scoreRepository.findByJudgeId(judgeId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ScoreResponse toResponse(Score score) {
        String judgeName = judgeRepository.findById(score.getJudgeId())
                .map(Judge::getName)
                .orElse("未知评委");

        return ScoreResponse.builder()
                .id(score.getId())
                .scriptId(score.getScriptId())
                .judgeId(score.getJudgeId())
                .judgeName(judgeName)
                .creativityScore(score.getCreativityScore())
                .commercialScore(score.getCommercialScore())
                .difficultyScore(score.getDifficultyScore())
                .logicScore(score.getLogicScore())
                .audienceScore(score.getAudienceScore())
                .comment(score.getComment())
                .isExtreme(score.getIsExtreme())
                .valid(score.getValid())
                .invalidatedAt(score.getInvalidatedAt() != null
                        ? score.getInvalidatedAt().format(FORMATTER) : null)
                .invalidatedReason(score.getInvalidatedReason())
                .createdAt(score.getCreatedAt() != null ? score.getCreatedAt().format(FORMATTER) : null)
                .build();
    }
}
