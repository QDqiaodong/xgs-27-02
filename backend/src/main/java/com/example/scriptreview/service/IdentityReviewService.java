package com.example.scriptreview.service;

import com.example.scriptreview.dto.request.DecideIdentityGroupRequest;
import com.example.scriptreview.dto.response.IdentityMapResponse;
import com.example.scriptreview.dto.response.ScriptRawResponse;
import com.example.scriptreview.entity.DesensitizationAudit;
import com.example.scriptreview.entity.IdentityClue;
import com.example.scriptreview.entity.IdentityGroup;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.repository.DesensitizationAuditRepository;
import com.example.scriptreview.repository.IdentityClueRepository;
import com.example.scriptreview.repository.IdentityGroupRepository;
import com.example.scriptreview.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员侧双盲脱敏管控：
 * - 原稿与代号映射是受控视图，每次查看都记录“谁、何时、看了什么”；
 * - 管理员对可疑身份分组的核对只调整脱敏视图与映射，绝不改写原稿；
 * - 核对后评委侧立即从“保守隐藏”切换为“身份代号替换”或“剧情人物保留”。
 */
@Slf4j
@Service
public class IdentityReviewService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int RECENT_AUDIT_LIMIT = 20;

    private final ScriptRepository scriptRepository;
    private final IdentityGroupRepository groupRepository;
    private final IdentityClueRepository clueRepository;
    private final DesensitizationAuditRepository auditRepository;
    private final DesensitizationService desensitizationService;
    /** 自注入代理：让“核对结论落库”先在独立事务提交，随后组装的映射视图才能读到最新状态。 */
    private final IdentityReviewService self;

    public IdentityReviewService(ScriptRepository scriptRepository,
                                 IdentityGroupRepository groupRepository,
                                 IdentityClueRepository clueRepository,
                                 DesensitizationAuditRepository auditRepository,
                                 DesensitizationService desensitizationService,
                                 @Lazy IdentityReviewService self) {
        this.scriptRepository = scriptRepository;
        this.groupRepository = groupRepository;
        this.clueRepository = clueRepository;
        this.auditRepository = auditRepository;
        this.desensitizationService = desensitizationService;
        this.self = self;
    }

    @Transactional
    public ScriptRawResponse viewRaw(Long scriptId, User viewer, String ip) {
        Script script = requireScript(scriptId);
        requireAdmin(viewer);
        recordAudit(scriptId, "VIEW_RAW", viewer, ip, "查看脚本原稿（含作者/来源机构/联系方式/正文原文）");
        return ScriptRawResponse.builder()
                .id(script.getId())
                .title(script.getTitle())
                .genre(script.getGenre())
                .duration(script.getDuration())
                .targetAudience(script.getTargetAudience())
                .author(script.getAuthor())
                .source(script.getSource())
                .contactInfo(script.getContactInfo())
                .description(script.getDescription())
                .attachmentNote(script.getAttachmentNote())
                .status(script.getStatus())
                .grade(script.getGrade())
                .totalScore(script.getTotalScore())
                .createdAt(fmt(script.getCreatedAt()))
                .updatedAt(fmt(script.getUpdatedAt()))
                .build();
    }

    /**
     * 管理员核对身份分组（编排方法，不开事务）：
     * 先让核对结论在独立事务中提交，再组装映射视图——这样 buildMap 经代理开启的
     * 只读/新事务一定能读到刚提交的状态，不会在响应里返回核对前的旧代号。
     * 核对只更新脱敏视图与映射，原稿任何字段都不改动。
     */
    public IdentityMapResponse decideGroup(Long scriptId, Long groupId,
                                           DecideIdentityGroupRequest request, User viewer, String ip) {
        requireAdmin(viewer);
        String summary = self.persistDecision(scriptId, groupId, request, viewer, ip);
        log.info("管理员{}完成脚本{}身份核对：{}", viewer.getName(), scriptId, summary);
        // viewMap 经代理执行：只读事务 + 查看审计 + 读取已提交的最新映射
        return self.viewMapAfterDecision(scriptId, viewer);
    }

    /** 落库核对结论并写审计，独立事务先提交。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String persistDecision(Long scriptId, Long groupId,
                                  DecideIdentityGroupRequest request, User viewer, String ip) {
        Script script = requireScript(scriptId);
        IdentityGroup group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("身份分组不存在"));
        if (!group.getScriptId().equals(scriptId)) {
            throw new IllegalArgumentException("身份分组不属于该脚本");
        }
        String previous = group.getStatus();
        group.setStatus(request.getDecision());
        group.setDecidedBy(viewer.getId());
        group.setDecidedByName(viewer.getName());
        group.setDecidedAt(java.time.LocalDateTime.now());
        groupRepository.save(group);

        String detail = String.format("核对分组#%d（%s，原文：%s）：%s → %s",
                groupId, DesensitizationService.typeLabel(group.getClueType()),
                group.getRepresentative(), statusLabel(previous), statusLabel(request.getDecision()));
        recordAudit(scriptId, "DECIDE_GROUP", viewer, ip, detail);
        return detail;
    }

    /** 核对完成后回显最新映射（不再追加 VIEW_MAP 审计，核对动作本身已审计）。 */
    @Transactional(readOnly = true)
    public IdentityMapResponse viewMapAfterDecision(Long scriptId, User viewer) {
        requireAdmin(viewer);
        Script script = requireScript(scriptId);
        return buildMap(script);
    }

    /** 代号映射核对视图：原始内容 / 识别线索 / 脱敏结果 三区分明；查看即写审计，故非只读事务。 */
    @Transactional
    public IdentityMapResponse viewMap(Long scriptId, User viewer, String ip) {
        Script script = requireScript(scriptId);
        requireAdmin(viewer);
        recordAudit(scriptId, "VIEW_MAP", viewer, ip, "查看原稿-代号映射与待核对线索");
        return buildMap(script);
    }

    private IdentityMapResponse buildMap(Script script) {
        Long scriptId = script.getId();
        // 确保识别与原稿一致（REQUIRES_NEW 独立事务落库），管理员看到的线索/代号就是评委侧实际生效的版本
        DesensitizationService.MaskModel model = desensitizationService.ensureAnalyzed(script);

        List<IdentityGroup> groups = groupRepository.findByScriptIdOrderByLabelLetterAscIdAsc(scriptId);
        List<IdentityClue> clues = clueRepository.findByScriptIdOrderByOrdinalAsc(scriptId);
        Map<Long, List<IdentityClue>> cluesByGroup = new HashMap<>();
        for (IdentityClue clue : clues) {
            cluesByGroup.computeIfAbsent(clue.getGroupId(), k -> new java.util.ArrayList<>()).add(clue);
        }

        String maskedTitle = desensitizationService.maskField(model,
                DesensitizationService.FIELD_TITLE, script.getTitle()).orElse(script.getTitle());
        String maskedDescription = desensitizationService.maskField(model,
                DesensitizationService.FIELD_DESCRIPTION, script.getDescription()).orElse(null);
        String maskedAttachment = desensitizationService.maskField(model,
                DesensitizationService.FIELD_ATTACHMENT_NOTE, script.getAttachmentNote()).orElse(null);
        // 元数据身份字段在脱敏视图中整体替换（无身份线索时为 null，前端显示“未填写/无机构线索”）
        String maskedAuthor = desensitizationService.maskField(model,
                DesensitizationService.FIELD_AUTHOR, script.getAuthor()).orElse(null);
        String maskedSource = desensitizationService.maskField(model,
                DesensitizationService.FIELD_SOURCE, script.getSource()).orElse(null);

        List<IdentityMapResponse.GroupView> groupViews = groups.stream()
                .map(g -> toGroupView(g, script, cluesByGroup.getOrDefault(g.getId(), List.of())))
                .toList();

        return IdentityMapResponse.builder()
                .scriptId(scriptId)
                .scriptSalt(script.getMaskSalt())
                .raw(IdentityMapResponse.RawSection.builder()
                        .title(script.getTitle())
                        .author(script.getAuthor())
                        .source(script.getSource())
                        .contactInfo(script.getContactInfo())
                        .description(script.getDescription())
                        .attachmentNote(script.getAttachmentNote())
                        .build())
                .groups(groupViews)
                .masked(IdentityMapResponse.MaskedSection.builder()
                        .title(maskedTitle)
                        .description(maskedDescription)
                        .attachmentNote(maskedAttachment)
                        .authorCode(maskedAuthor)
                        .sourceCode(maskedSource)
                        .build())
                .recentAudits(auditRepository
                        .findByScriptIdOrderByCreatedAtDescIdDesc(scriptId, PageRequest.of(0, RECENT_AUDIT_LIMIT))
                        .stream().map(this::toAuditView).toList())
                .pendingCount(model.pendingCount)
                .build();
    }

    private IdentityMapResponse.GroupView toGroupView(IdentityGroup group, Script script,
                                                       List<IdentityClue> clues) {
        String code = switch (group.getStatus()) {
            case DesensitizationService.STATUS_CHARACTER -> "剧情人物（保留原文）";
            case DesensitizationService.STATUS_PENDING ->
                    desensitizationService.pendingToken(group, script.getMaskSalt());
            default -> desensitizationService.codeToken(group, script.getMaskSalt());
        };

        List<IdentityMapResponse.ClueView> clueViews = clues.stream()
                .map(c -> IdentityMapResponse.ClueView.builder()
                        .id(c.getId())
                        .groupId(c.getGroupId())
                        .fieldName(c.getFieldName())
                        .fieldLabel(fieldLabel(c.getFieldName()))
                        .startOffset(c.getStartOffset())
                        .endOffset(c.getEndOffset())
                        .matchedText(c.getMatchedText())
                        .reason(c.getDetectReason())
                        .reasonLabel(reasonLabel(c.getDetectReason()))
                        .build())
                .toList();

        return IdentityMapResponse.GroupView.builder()
                .id(group.getId())
                .clueType(group.getClueType())
                .clueTypeLabel(DesensitizationService.typeLabel(group.getClueType()))
                .labelLetter(group.getLabelLetter())
                .code(code)
                .status(group.getStatus())
                .statusLabel(statusLabel(group.getStatus()))
                .representative(group.getRepresentative())
                .detectReason(clues.isEmpty() ? null : clues.get(0).getDetectReason())
                .detectReasonLabel(clues.isEmpty() ? null : reasonLabel(clues.get(0).getDetectReason()))
                .clueCount(clues.size())
                .clues(clueViews)
                .decidedByName(group.getDecidedByName())
                .decidedAt(group.getDecidedAt() != null ? group.getDecidedAt().format(FORMATTER) : null)
                .build();
    }

    private IdentityMapResponse.AuditView toAuditView(DesensitizationAudit a) {
        return IdentityMapResponse.AuditView.builder()
                .id(a.getId())
                .action(a.getAction())
                .actionLabel(actionLabel(a.getAction()))
                .viewerName(a.getViewerName())
                .ipAddress(a.getIpAddress())
                .detail(a.getDetail())
                .createdAt(fmt(a.getCreatedAt()))
                .build();
    }

    private void recordAudit(Long scriptId, String action, User viewer, String ip, String detail) {
        auditRepository.save(DesensitizationAudit.builder()
                .scriptId(scriptId)
                .action(action)
                .viewerId(viewer.getId())
                .viewerName(viewer.getName())
                .ipAddress(ip)
                .detail(detail)
                .createdAt(java.time.LocalDateTime.now())
                .build());
    }

    private Script requireScript(Long scriptId) {
        return scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("脚本不存在"));
    }

    private void requireAdmin(User user) {
        if (!"ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("只有管理员可以查看原稿或代号映射");
        }
    }

    public static String statusLabel(String status) {
        return switch (status) {
            case DesensitizationService.STATUS_PENDING -> "待管理员核对";
            case DesensitizationService.STATUS_IDENTITY -> "确认投稿身份";
            case DesensitizationService.STATUS_CHARACTER -> "确认剧情人物";
            default -> status;
        };
    }

    public static String fieldLabel(String field) {
        return switch (field) {
            case "AUTHOR" -> "作者（元数据）";
            case "SOURCE" -> "来源机构（元数据）";
            case "CONTACT_INFO" -> "联系方式";
            case "TITLE" -> "标题";
            case "DESCRIPTION" -> "正文";
            case "ATTACHMENT_NOTE" -> "附件说明";
            default -> field;
        };
    }

    public static String reasonLabel(String reason) {
        return switch (reason) {
            case DesensitizationService.REASON_METADATA -> "元数据已知身份";
            case DesensitizationService.REASON_PATTERN -> "模式识别（电话/邮箱/账号/机构）";
            case DesensitizationService.REASON_NAME_CONTEXT -> "身份语境";
            case DesensitizationService.REASON_NAME_BARE -> "无明确语境（待核对）";
            case "CHARACTER" -> "剧情语境";
            default -> reason;
        };
    }

    public static String actionLabel(String action) {
        return switch (action) {
            case "VIEW_RAW" -> "查看原稿";
            case "VIEW_MAP" -> "查看代号映射";
            case "DECIDE_GROUP" -> "核对身份";
            default -> action;
        };
    }

    private String fmt(java.time.LocalDateTime time) {
        return time != null ? time.format(FORMATTER) : null;
    }
}
