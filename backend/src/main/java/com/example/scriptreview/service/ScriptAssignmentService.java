package com.example.scriptreview.service;

import com.example.scriptreview.dto.request.AssignScriptRequest;
import com.example.scriptreview.dto.response.ScriptAssignmentResponse;
import com.example.scriptreview.entity.Judge;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.entity.ScriptAssignment;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.exception.ConflictException;
import com.example.scriptreview.repository.JudgeRepository;
import com.example.scriptreview.repository.ScriptAssignmentRepository;
import com.example.scriptreview.repository.ScriptRepository;
import com.example.scriptreview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScriptAssignmentService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ScriptAssignmentRepository assignmentRepository;
    private final ScriptRepository scriptRepository;
    private final JudgeRepository judgeRepository;
    private final UserRepository userRepository;
    private final ScriptStatusService scriptStatusService;
    private final DesensitizationService desensitizationService;

    /** 分配操作只能由管理员发起，返回标题保持原文；未找到用户时按管理员占位处理。 */
    private User adminUser(Long adminId) {
        return userRepository.findById(adminId).orElseGet(() ->
                User.builder().id(adminId).role("ADMIN").name("管理员").build());
    }

    @Transactional
    public List<ScriptAssignmentResponse> assignScript(AssignScriptRequest request, Long assignedBy) {
        Script script = scriptRepository.findByIdForUpdate(request.getScriptId())
                .orElseThrow(() -> new RuntimeException("脚本不存在"));

        List<ScriptAssignmentResponse> responses = new ArrayList<>();

        for (Long judgeId : request.getJudgeIds()) {
            Judge judge = judgeRepository.findById(judgeId)
                    .orElseThrow(() -> new RuntimeException("评委不存在: " + judgeId));
            if (!"ACTIVE".equals(judge.getStatus())) {
                throw new RuntimeException("评委已停用，不能分配: " + judge.getName());
            }

            if (assignmentRepository.findByScriptIdAndJudgeId(request.getScriptId(), judgeId).isPresent()) {
                continue;
            }

            ScriptAssignment assignment = ScriptAssignment.builder()
                    .scriptId(request.getScriptId())
                    .judgeId(judgeId)
                    .assignedBy(assignedBy)
                    .status("ASSIGNED")
                    .deadline(request.getDeadline() != null ?
                            LocalDateTime.parse(request.getDeadline()) : null)
                    .build();

            ScriptAssignment saved = assignmentRepository.save(assignment);
            responses.add(toResponse(saved, adminUser(assignedBy)));
        }

        scriptStatusService.recomputeStatus(request.getScriptId());

        return responses;
    }

    /** 分配列表：管理员可看全部；评委只能看到与自己相关的分配（替补仅看到接管后的新任务）。 */
    @Transactional(readOnly = true)
    public List<ScriptAssignmentResponse> getAssignments(User currentUser, Long scriptId, Long judgeId) {
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        Long selfJudgeIdRaw = null;
        if ("JUDGE".equals(currentUser.getRole())) {
            selfJudgeIdRaw = judgeRepository.findByUserId(currentUser.getId())
                    .map(Judge::getId)
                    .orElseThrow(() -> new AccessDeniedException("当前账号未关联评委身份"));
        } else if (!isAdmin) {
            throw new AccessDeniedException("无权查看分配记录");
        }
        final Long selfJudgeId = selfJudgeIdRaw;

        final Long filterJudgeId = isAdmin ? judgeId : selfJudgeId;
        final Long filterScriptId = scriptId;

        List<ScriptAssignment> assignments;
        if (filterScriptId != null && filterJudgeId != null) {
            assignments = assignmentRepository.findByScriptId(filterScriptId).stream()
                    .filter(a -> a.getJudgeId().equals(filterJudgeId))
                    .collect(Collectors.toList());
        } else if (filterScriptId != null) {
            assignments = assignmentRepository.findByScriptId(filterScriptId);
        } else if (filterJudgeId != null) {
            assignments = assignmentRepository.findByJudgeId(filterJudgeId);
        } else {
            assignments = assignmentRepository.findAll();
        }

        return assignments.stream()
                .filter(a -> isAdmin || a.getJudgeId().equals(selfJudgeId))
                .map(a -> toResponse(a, currentUser))
                .collect(Collectors.toList());
    }

    public List<ScriptAssignmentResponse> getAssignmentsByScriptId(Long scriptId, User currentUser) {
        return assignmentRepository.findByScriptId(scriptId).stream()
                .map(a -> toResponse(a, currentUser))
                .collect(Collectors.toList());
    }

    public List<ScriptAssignmentResponse> getAssignmentsByJudgeId(Long judgeId, User currentUser) {
        return assignmentRepository.findByJudgeId(judgeId).stream()
                .map(a -> toResponse(a, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional
    public void completeAssignment(Long scriptId, Long judgeId) {
        ScriptAssignment assignment = assignmentRepository.findByScriptIdAndJudgeId(scriptId, judgeId)
                .orElseThrow(() -> new RuntimeException("分配记录不存在"));

        assignment.setStatus("COMPLETED");
        assignmentRepository.save(assignment);
    }

    private ScriptAssignmentResponse toResponse(ScriptAssignment assignment, User viewer) {
        Script scriptEntity = scriptRepository.findById(assignment.getScriptId()).orElse(null);
        // 分配记录中的脚本标题对评委/查看员脱敏，管理员保留原标题
        String scriptTitle = scriptEntity == null ? "未知脚本"
                : desensitizationService.maskTitleFor(scriptEntity, viewer);

        String judgeName = judgeRepository.findById(assignment.getJudgeId())
                .map(Judge::getName)
                .orElse("未知评委");

        String assignedByName = userRepository.findById(assignment.getAssignedBy())
                .map(User::getName)
                .orElse("未知");

        return ScriptAssignmentResponse.builder()
                .id(assignment.getId())
                .scriptId(assignment.getScriptId())
                .scriptTitle(scriptTitle)
                .judgeId(assignment.getJudgeId())
                .judgeName(judgeName)
                .assignedBy(assignment.getAssignedBy())
                .assignedByName(assignedByName)
                .status(assignment.getStatus())
                .deadline(assignment.getDeadline() != null ?
                        assignment.getDeadline().format(FORMATTER) : null)
                .assignedAt(assignment.getAssignedAt() != null ?
                        assignment.getAssignedAt().format(FORMATTER) : null)
                .recusedAt(assignment.getRecusedAt() != null ?
                        assignment.getRecusedAt().format(FORMATTER) : null)
                .recusalApplicationId(assignment.getRecusalApplicationId())
                .replacesAssignmentId(assignment.getReplacesAssignmentId())
                .build();
    }
}
