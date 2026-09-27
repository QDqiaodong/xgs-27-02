package com.example.scriptreview.service;

import com.example.scriptreview.dto.request.CreateRecusalRequest;
import com.example.scriptreview.dto.request.DecideRecusalRequest;
import com.example.scriptreview.dto.request.SubstituteJudgeRequest;
import com.example.scriptreview.exception.ConflictException;
import com.example.scriptreview.entity.*;
import com.example.scriptreview.repository.*;
import com.example.scriptreview.dto.response.RecusalApplicationResponse;
import com.example.scriptreview.dto.response.RecusalReviewLogResponse;
import com.example.scriptreview.dto.response.ScriptAssignmentResponse;
import com.example.scriptreview.dto.response.SubstituteCandidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecusalService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> OPEN_STATUSES = new HashSet<>(Arrays.asList("PENDING", "SUPPLEMENT_REQUESTED"));
    private static final Set<String> RECUSAL_TYPES =
            new HashSet<>(Arrays.asList("AUTHOR", "ORGANIZATION", "TOPIC", "OTHER"));

    private final RecusalApplicationRepository recusalRepository;
    private final RecusalReviewLogRepository reviewLogRepository;
    private final ScriptRepository scriptRepository;
    private final ScriptAssignmentRepository assignmentRepository;
    private final ScoreRepository scoreRepository;
    private final JudgeRepository judgeRepository;
    private final UserRepository userRepository;
    private final ScriptStatusService scriptStatusService;
    private final DesensitizationService desensitizationService;

    // ============================ 申报 ============================

    @Transactional
    public RecusalApplicationResponse create(CreateRecusalRequest request, User currentUser) {
        if (!RECUSAL_TYPES.contains(request.getType())) {
            throw new RuntimeException("回避类型不合法");
        }

        Script script = scriptRepository.findByIdForUpdate(request.getScriptId())
                .orElseThrow(() -> new RuntimeException("脚本不存在"));
        Judge judge = judgeRepository.findById(request.getJudgeId())
                .orElseThrow(() -> new RuntimeException("评委不存在"));
        if (!"ACTIVE".equals(judge.getStatus())) {
            throw new RuntimeException("该评委账号已停用，不能申报回避");
        }

        ScriptAssignment assignment = assignmentRepository
                .findByScriptIdAndJudgeId(script.getId(), judge.getId())
                .orElseThrow(() -> new RuntimeException("该评委未被分配到此脚本，不能申报回避"));
        if ("RECUSED".equals(assignment.getStatus())) {
            throw new ConflictException("该分配已因回避终止，不能重复申报");
        }

        // 权限：评委只能为自己申报；管理员可以代替评委申报
        boolean onBehalf = false;
        if ("JUDGE".equals(currentUser.getRole())) {
            Judge ownJudge = judgeRepository.findByUserId(currentUser.getId()).orElse(null);
            if (ownJudge == null || !ownJudge.getId().equals(judge.getId())) {
                throw new AccessDeniedException("评委只能申报本人在已分配脚本上的回避");
            }
        } else if (!"ADMIN".equals(currentUser.getRole())) {
            throw new AccessDeniedException("无权申报回避");
        } else {
            onBehalf = true;
        }

        // 同一评委同一脚本存在未结束申请时不允许重复申报（加锁读，防并发重复申报）
        List<RecusalApplication> existing = recusalRepository.findByScriptIdForUpdate(script.getId()).stream()
                .filter(app -> app.getJudgeId().equals(judge.getId()))
                .collect(Collectors.toList());
        for (RecusalApplication app : existing) {
            if (OPEN_STATUSES.contains(app.getStatus())) {
                throw new ConflictException("该评委在此脚本上已有未处理的回避申请");
            }
            if ("APPROVED".equals(app.getStatus())) {
                throw new ConflictException("该评委在此脚本上的回避申请已批准，不能再次申报");
            }
        }

        RecusalApplication application = RecusalApplication.builder()
                .scriptId(script.getId())
                .judgeId(judge.getId())
                .assignmentId(assignment.getId())
                .type(request.getType())
                .reason(request.getReason())
                .evidence(request.getEvidence())
                .status("PENDING")
                .submittedBy(currentUser.getId())
                .version(0L)
                .build();
        RecusalApplication saved = recusalRepository.save(application);

        logAction(saved.getId(), "CREATE", currentUser, null, "PENDING",
                (onBehalf ? "管理员代为申报；" : "评委本人申报；") + "类型=" + saved.getType());

        return toResponse(saved, currentUser);
    }

    // ============================ 补充材料 ============================

    @Transactional
    public RecusalApplicationResponse supplement(Long applicationId, String supplementInfo, User currentUser) {
        RecusalApplication application = recusalRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new RuntimeException("回避申请不存在"));

        if (!"SUPPLEMENT_REQUESTED".equals(application.getStatus())) {
            throw new ConflictException("只有处于待补充状态的申请才能提交补充材料");
        }
        ensureOwnerOrAdmin(application, currentUser);

        String from = application.getStatus();
        application.setSupplementInfo(supplementInfo);
        application.setStatus("PENDING");
        RecusalApplication saved = recusalRepository.save(application);

        logAction(saved.getId(), "SUPPLEMENT", currentUser, from, "PENDING", "评委提交补充材料");
        return toResponse(saved, currentUser);
    }

    // ============================ 管理员处理 ============================

    @Transactional
    public RecusalApplicationResponse decide(Long applicationId, DecideRecusalRequest request, User currentUser) {
        if (!"ADMIN".equals(currentUser.getRole())) {
            throw new AccessDeniedException("只有管理员可以处理回避申请");
        }
        String decision = request.getDecision();
        if (!Arrays.asList("APPROVE", "REJECT", "REQUEST_SUPPLEMENT").contains(decision)) {
            throw new RuntimeException("处理决定不合法");
        }

        // 统一加锁顺序：先脚本行、后申请行，避免与申报/分级/打分事务交叉死锁；
        // 行锁 + 状态守卫 + 乐观锁：两名管理员并发处理时只有一个决定生效
        RecusalApplication preliminary = recusalRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("回避申请不存在"));
        scriptRepository.findByIdForUpdate(preliminary.getScriptId())
                .orElseThrow(() -> new RuntimeException("脚本不存在"));
        RecusalApplication application = recusalRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new RuntimeException("回避申请不存在"));

        if (!OPEN_STATUSES.contains(application.getStatus())) {
            throw new ConflictException("该回避申请已处理，当前状态：" + application.getStatus());
        }
        // 不能审批自己代替评委提交的申请
        if (application.getSubmittedBy().equals(currentUser.getId())) {
            throw new AccessDeniedException("不能审批由自己代为提交的回避申请，请由其他管理员处理");
        }

        String from = application.getStatus();

        switch (decision) {
            case "APPROVE":
                approve(application, request.getComment(), currentUser);
                break;
            case "REJECT":
                reject(application, request.getComment(), currentUser, from);
                break;
            default:
                application.setStatus("SUPPLEMENT_REQUESTED");
                application.setDecisionComment(request.getComment());
                application.setDecidedBy(currentUser.getId());
                application.setDecidedByName(currentUser.getName());
                application.setDecidedAt(LocalDateTime.now());
                recusalRepository.save(application);
                logAction(application.getId(), "REQUEST_SUPPLEMENT", currentUser, from,
                        "SUPPLEMENT_REQUESTED", request.getComment());
        }

        return toResponse(application, currentUser);
    }

    private void approve(RecusalApplication application, String comment, User admin) {
        String from = application.getStatus();

        // 按统一顺序锁定：脚本行 -> 分配集 -> 评分集，避免与分级/打分事务交叉死锁
        Script script = scriptRepository.findByIdForUpdate(application.getScriptId())
                .orElseThrow(() -> new RuntimeException("脚本不存在"));
        List<ScriptAssignment> assignments =
                assignmentRepository.findByScriptIdForUpdate(script.getId());
        List<Score> scores = scoreRepository.findByScriptIdForUpdate(script.getId());

        // 立即终止原评委未完成（或已完成）的分配
        ScriptAssignment assignment = assignments.stream()
                .filter(a -> a.getId().equals(application.getAssignmentId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("对应分配记录不存在"));
        assignment.setStatus("RECUSED");
        assignment.setRecusedAt(LocalDateTime.now());
        assignment.setRecusalApplicationId(application.getId());
        assignmentRepository.save(assignment);

        // 旧评分不删除：标记无效、保留原文，从总分/极端分/等级计算中排除
        List<Score> ownScores = scores.stream()
                .filter(s -> s.getJudgeId().equals(application.getJudgeId()))
                .collect(Collectors.toList());
        for (Score score : ownScores) {
            score.setValid(false);
            score.setIsExtreme(false);
            score.setInvalidatedAt(LocalDateTime.now());
            score.setInvalidatedReason("回避申请#" + application.getId() + "批准");
            scoreRepository.save(score);
        }

        // 已经分级的脚本：撤销旧等级进入待补评
        boolean wasGraded = "GRADED".equals(script.getStatus());
        application.setStatus("APPROVED");
        application.setDecisionComment(comment);
        application.setDecidedBy(admin.getId());
        application.setDecidedByName(admin.getName());
        application.setDecidedAt(LocalDateTime.now());
        recusalRepository.save(application);

        if (wasGraded) {
            script.setGrade(null);
            script.setTotalScore(null);
            script.setStatus("PENDING_SUPPLEMENT");
            scriptRepository.save(script);
        } else {
            scriptStatusService.recomputeStatus(script.getId());
        }

        logAction(application.getId(), "APPROVE", admin, from, "APPROVED", comment);
    }

    private void reject(RecusalApplication application, String comment, User admin, String from) {
        Script script = scriptRepository.findByIdForUpdate(application.getScriptId())
                .orElseThrow(() -> new RuntimeException("脚本不存在"));
        List<ScriptAssignment> assignments =
                assignmentRepository.findByScriptIdForUpdate(script.getId());
        List<Score> scores = scoreRepository.findByScriptIdForUpdate(script.getId());

        // 恢复原分配状态与原评分有效性
        ScriptAssignment assignment = assignments.stream()
                .filter(a -> a.getId().equals(application.getAssignmentId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("对应分配记录不存在"));
        if (application.getId().equals(assignment.getRecusalApplicationId())
                && "RECUSED".equals(assignment.getStatus())) {
            boolean hadScore = scores.stream().anyMatch(s ->
                    s.getJudgeId().equals(application.getJudgeId()) && !Boolean.TRUE.equals(s.getValid()));
            assignment.setStatus(hadScore ? "COMPLETED" : "ASSIGNED");
            assignment.setRecusedAt(null);
            assignment.setRecusalApplicationId(null);
            assignmentRepository.save(assignment);
        }

        for (Score score : scores) {
            if (score.getJudgeId().equals(application.getJudgeId())
                    && !Boolean.TRUE.equals(score.getValid())) {
                score.setValid(true);
                score.setInvalidatedAt(null);
                score.setInvalidatedReason(null);
                scoreRepository.save(score);
            }
        }

        application.setStatus("REJECTED");
        application.setDecisionComment(comment);
        application.setDecidedBy(admin.getId());
        application.setDecidedByName(admin.getName());
        application.setDecidedAt(LocalDateTime.now());
        recusalRepository.save(application);

        // 驳回只会发生在申请未批准的流程上，脚本状态通常未变；仍统一重算保持一致
        scriptStatusService.recomputeStatus(script.getId());

        logAction(application.getId(), "REJECT", admin, from, "REJECTED", comment);
    }

    // ============================ 替补接管 ============================

    @Transactional
    public ScriptAssignmentResponse assignSubstitute(Long applicationId, SubstituteJudgeRequest request, User admin) {
        if (!"ADMIN".equals(admin.getRole())) {
            throw new AccessDeniedException("只有管理员可以指定替补评委");
        }

        // 统一加锁顺序：先脚本行、后申请行，防止与审批/分级事务交叉死锁
        RecusalApplication preliminary = recusalRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("回避申请不存在"));
        if (!"APPROVED".equals(preliminary.getStatus())) {
            throw new ConflictException("只有已批准的回避申请才能指定替补评委");
        }
        Script script = scriptRepository.findByIdForUpdate(preliminary.getScriptId())
                .orElseThrow(() -> new RuntimeException("脚本不存在"));
        RecusalApplication application = recusalRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new RuntimeException("回避申请不存在"));
        if (!"APPROVED".equals(application.getStatus())) {
            throw new ConflictException("只有已批准的回避申请才能指定替补评委");
        }

        List<ScriptAssignment> assignments = assignmentRepository.findByScriptIdForUpdate(script.getId());

        // 唯一约束 uk_replaces_assignment + 业务检查：不能重复创建替补分配
        if (assignmentRepository.findByReplacesAssignmentId(application.getAssignmentId()).isPresent()) {
            throw new ConflictException("该回避申请已完成替补接管，不能重复指定");
        }

        Long substituteJudgeId = request.getSubstituteJudgeId();
        Judge substitute = judgeRepository.findById(substituteJudgeId)
                .orElseThrow(() -> new RuntimeException("替补评委不存在"));
        if (!"ACTIVE".equals(substitute.getStatus())) {
            throw new RuntimeException("该评委账号状态异常，不能作为替补评委");
        }

        // 尚未参与该脚本：不能有任何历史分配（包括已回避/已完成），也不能有评分
        boolean participated = assignments.stream().anyMatch(a -> a.getJudgeId().equals(substituteJudgeId));
        if (participated) {
            throw new ConflictException("该评委已参与过此脚本，不能作为替补");
        }
        boolean hasScore = !scoreRepository.findByScriptIdAndJudgeId(script.getId(), substituteJudgeId).isEmpty();
        if (hasScore) {
            throw new ConflictException("该评委已对此脚本打过分，不能作为替补");
        }
        // 不能由原评委本人接管
        if (substituteJudgeId.equals(application.getJudgeId())) {
            throw new ConflictException("不能指定被回避的原评委作为替补");
        }
        // 专业方向匹配
        if (!expertiseMatches(substitute.getExpertise(), script.getGenre())) {
            throw new ConflictException("替补评委专业方向与脚本题材不匹配：" + script.getGenre());
        }

        ScriptAssignment replacement = ScriptAssignment.builder()
                .scriptId(script.getId())
                .judgeId(substituteJudgeId)
                .assignedBy(admin.getId())
                .status("ASSIGNED")
                .deadline(request.getDeadline())
                .replacesAssignmentId(application.getAssignmentId())
                .build();
        ScriptAssignment saved = assignmentRepository.save(replacement);

        // 替补接管后重算状态：仍未补齐有效评分时保持“待补评”，达到人数则回到评审中
        scriptStatusService.recomputeStatus(script.getId());

        logAction(application.getId(), "SUBSTITUTE", admin, "APPROVED", "APPROVED",
                "指定替补评委：" + substitute.getName());

        return assignmentToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SubstituteCandidateResponse> getSubstituteCandidates(Long applicationId, User currentUser) {
        if (!"ADMIN".equals(currentUser.getRole())) {
            throw new AccessDeniedException("只有管理员可以查看替补候选名单");
        }
        RecusalApplication application = recusalRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("回避申请不存在"));
        Script script = scriptRepository.findById(application.getScriptId())
                .orElseThrow(() -> new RuntimeException("脚本不存在"));

        Set<Long> participatedJudgeIds = assignmentRepository.findByScriptId(script.getId()).stream()
                .map(ScriptAssignment::getJudgeId)
                .collect(Collectors.toSet());
        Set<Long> scoredJudgeIds = scoreRepository.findByScriptId(script.getId()).stream()
                .map(Score::getJudgeId)
                .collect(Collectors.toSet());

        return judgeRepository.findAll().stream()
                .filter(j -> "ACTIVE".equals(j.getStatus()))
                .filter(j -> !participatedJudgeIds.contains(j.getId()))
                .filter(j -> !scoredJudgeIds.contains(j.getId()))
                .filter(j -> !j.getId().equals(application.getJudgeId()))
                .map(j -> SubstituteCandidateResponse.builder()
                        .judgeId(j.getId())
                        .judgeName(j.getName())
                        .expertise(j.getExpertise())
                        .expertiseMatched(expertiseMatches(j.getExpertise(), script.getGenre()))
                        .build())
                .sorted(Comparator.comparing(c -> Boolean.FALSE.equals(c.getExpertiseMatched())))
                .collect(Collectors.toList());
    }

    /** 专业方向与题材匹配：专长文本包含题材关键词，或命中常见题材同义映射。 */
    private boolean expertiseMatches(String expertise, String genre) {
        if (expertise == null || expertise.isBlank() || genre == null) {
            return false;
        }
        String exp = expertise.replace("类脚本", "").replace("类", "");
        if (exp.contains(genre)) {
            return true;
        }
        Map<String, List<String>> synonyms = new HashMap<>();
        synonyms.put("情感", Arrays.asList("生活", "职场"));
        synonyms.put("美食", Arrays.asList("餐饮", "探店"));
        synonyms.put("旅游", Arrays.asList("旅行", "风光"));
        synonyms.put("科技", Arrays.asList("科普", "AI"));
        synonyms.put("搞笑", Arrays.asList("喜剧", "日常"));
        for (Map.Entry<String, List<String>> entry : synonyms.entrySet()) {
            if (genre.contains(entry.getKey()) || entry.getValue().stream().anyMatch(genre::contains)) {
                if (exp.contains(entry.getKey()) || entry.getValue().stream().anyMatch(exp::contains)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ============================ 查询 ============================

    @Transactional(readOnly = true)
    public List<RecusalApplicationResponse> listAll(User currentUser) {
        if (!"ADMIN".equals(currentUser.getRole())) {
            throw new AccessDeniedException("只有管理员可以查看全部回避申请");
        }
        return recusalRepository.findAll().stream()
                .sorted(Comparator.comparing(RecusalApplication::getCreatedAt).reversed())
                .map(app -> toResponse(app, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecusalApplicationResponse> listByScript(Long scriptId, User currentUser) {
        return recusalRepository.findByScriptId(scriptId).stream()
                .sorted(Comparator.comparing(RecusalApplication::getCreatedAt).reversed())
                .map(app -> toResponse(app, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecusalApplicationResponse> listMine(User currentUser) {
        Judge judge = judgeRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("当前账号未关联评委身份"));
        return recusalRepository.findByJudgeId(judge.getId()).stream()
                .sorted(Comparator.comparing(RecusalApplication::getCreatedAt).reversed())
                .map(app -> toResponse(app, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RecusalApplicationResponse getDetail(Long applicationId, User currentUser) {
        RecusalApplication application = recusalRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("回避申请不存在"));
        // 查看完整内容前显式鉴权
        boolean sensitive = canViewSensitive(application, currentUser);
        if (!sensitive) {
            throw new AccessDeniedException("仅可查看回避状态，无权查看敏感说明");
        }
        return toResponse(application, currentUser);
    }

    @Transactional(readOnly = true)
    public List<RecusalReviewLogResponse> getReviewLogs(Long applicationId, User currentUser) {
        RecusalApplication application = recusalRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("回避申请不存在"));
        if (!canViewSensitive(application, currentUser)) {
            throw new AccessDeniedException("无权查看该回避申请的处理记录");
        }
        return reviewLogRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                .map(this::logToResponse)
                .collect(Collectors.toList());
    }

    // ============================ 权限与组装 ============================

    /** 完整内容（说明/依据/补充/理由）仅管理员与申报本人可见。 */
    private boolean canViewSensitive(RecusalApplication application, User currentUser) {
        if ("ADMIN".equals(currentUser.getRole())) {
            return true;
        }
        Judge judge = judgeRepository.findByUserId(currentUser.getId()).orElse(null);
        return judge != null && judge.getId().equals(application.getJudgeId());
    }

    private void ensureOwnerOrAdmin(RecusalApplication application, User currentUser) {
        if ("ADMIN".equals(currentUser.getRole())) {
            return;
        }
        Judge judge = judgeRepository.findByUserId(currentUser.getId()).orElse(null);
        if (judge == null || !judge.getId().equals(application.getJudgeId())) {
            throw new AccessDeniedException("只能由申报评委本人或管理员操作");
        }
    }

    private RecusalApplicationResponse toResponse(RecusalApplication app, User currentUser) {
        boolean sensitive = canViewSensitive(app, currentUser);

        Script scriptEntity = scriptRepository.findById(app.getScriptId()).orElse(null);
        // 评委/查看员看到的脚本标题同样经过脱敏，管理员保留原标题
        String scriptTitle = scriptEntity == null ? "未知脚本"
                : desensitizationService.maskTitleFor(scriptEntity, currentUser);
        String judgeName = judgeRepository.findById(app.getJudgeId())
                .map(Judge::getName).orElse("未知评委");
        String submitterName = userRepository.findById(app.getSubmittedBy())
                .map(User::getName).orElse("未知");
        String submitterRole = userRepository.findById(app.getSubmittedBy())
                .map(User::getRole).orElse(null);
        Judge judged = judgeRepository.findById(app.getJudgeId()).orElse(null);
        boolean onBehalf = judged == null || judged.getUserId() == null
                || !app.getSubmittedBy().equals(judged.getUserId());

        RecusalApplicationResponse.RecusalApplicationResponseBuilder builder = RecusalApplicationResponse.builder()
                .id(app.getId())
                .scriptId(app.getScriptId())
                .scriptTitle(scriptTitle)
                .judgeId(app.getJudgeId())
                .judgeName(judgeName)
                .assignmentId(app.getAssignmentId())
                .type(app.getType())
                .status(app.getStatus())
                .submittedBy(app.getSubmittedBy())
                .submittedByName(submitterName)
                .submittedByRole(submitterRole)
                .submittedOnBehalf(onBehalf)
                .decidedBy(app.getDecidedBy())
                .decidedByName(app.getDecidedByName())
                .decidedAt(app.getDecidedAt() != null ? app.getDecidedAt().format(FORMATTER) : null)
                .createdAt(app.getCreatedAt() != null ? app.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(app.getUpdatedAt() != null ? app.getUpdatedAt().format(FORMATTER) : null)
                .canViewSensitive(sensitive);

        if (sensitive) {
            builder.reason(app.getReason())
                    .evidence(app.getEvidence())
                    .supplementInfo(app.getSupplementInfo())
                    .decisionComment(app.getDecisionComment());
        }

        // 替补接管信息（非敏感状态）
        assignmentRepository.findByReplacesAssignmentId(app.getAssignmentId())
                .ifPresent(replacement -> {
                    builder.substituteAssignmentId(replacement.getId());
                    builder.substituteJudgeId(replacement.getJudgeId());
                    builder.substituteJudgeName(judgeRepository.findById(replacement.getJudgeId())
                            .map(Judge::getName).orElse("未知评委"));
                });

        return builder.build();
    }

    private ScriptAssignmentResponse assignmentToResponse(ScriptAssignment assignment) {
        String scriptTitle = scriptRepository.findById(assignment.getScriptId())
                .map(Script::getTitle).orElse("未知脚本");
        String judgeName = judgeRepository.findById(assignment.getJudgeId())
                .map(Judge::getName).orElse("未知评委");
        String assignedByName = userRepository.findById(assignment.getAssignedBy())
                .map(User::getName).orElse("未知");
        return ScriptAssignmentResponse.builder()
                .id(assignment.getId())
                .scriptId(assignment.getScriptId())
                .scriptTitle(scriptTitle)
                .judgeId(assignment.getJudgeId())
                .judgeName(judgeName)
                .assignedBy(assignment.getAssignedBy())
                .assignedByName(assignedByName)
                .status(assignment.getStatus())
                .deadline(assignment.getDeadline() != null ? assignment.getDeadline().format(FORMATTER) : null)
                .assignedAt(assignment.getAssignedAt() != null ? assignment.getAssignedAt().format(FORMATTER) : null)
                .recusedAt(assignment.getRecusedAt() != null ? assignment.getRecusedAt().format(FORMATTER) : null)
                .recusalApplicationId(assignment.getRecusalApplicationId())
                .replacesAssignmentId(assignment.getReplacesAssignmentId())
                .build();
    }

    private RecusalReviewLogResponse logToResponse(RecusalReviewLog log) {
        return RecusalReviewLogResponse.builder()
                .id(log.getId())
                .applicationId(log.getApplicationId())
                .action(log.getAction())
                .operatorId(log.getOperatorId())
                .operatorName(log.getOperatorName())
                .fromStatus(log.getFromStatus())
                .toStatus(log.getToStatus())
                .comment(log.getComment())
                .createdAt(log.getCreatedAt() != null ? log.getCreatedAt().format(FORMATTER) : null)
                .build();
    }

    private void logAction(Long applicationId, String action, User operator,
                           String fromStatus, String toStatus, String comment) {
        reviewLogRepository.save(RecusalReviewLog.builder()
                .applicationId(applicationId)
                .action(action)
                .operatorId(operator.getId())
                .operatorName(operator.getName())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .comment(comment)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
