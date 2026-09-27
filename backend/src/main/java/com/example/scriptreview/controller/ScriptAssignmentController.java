package com.example.scriptreview.controller;

import com.example.scriptreview.dto.request.AssignScriptRequest;
import com.example.scriptreview.dto.response.ScriptAssignmentResponse;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.service.ScriptAssignmentService;
import com.example.scriptreview.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class ScriptAssignmentController {

    private final ScriptAssignmentService assignmentService;

    @PostMapping
    public ResponseEntity<List<ScriptAssignmentResponse>> assignScript(@Valid @RequestBody AssignScriptRequest request) {
        User currentUser = SecurityUtils.requireRole("ADMIN");
        List<ScriptAssignmentResponse> response = assignmentService.assignScript(request, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    /** 管理员可带 scriptId/judgeId 过滤；评委仅能查看本人的分配记录（含替补接管后的任务）。 */
    @GetMapping
    public ResponseEntity<List<ScriptAssignmentResponse>> getAssignments(
            @RequestParam(required = false) Long scriptId,
            @RequestParam(required = false) Long judgeId) {
        User currentUser = SecurityUtils.getCurrentUser();
        List<ScriptAssignmentResponse> response =
                assignmentService.getAssignments(currentUser, scriptId, judgeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/script/{scriptId}")
    public ResponseEntity<List<ScriptAssignmentResponse>> getAssignmentsByScriptId(@PathVariable Long scriptId) {
        User currentUser = SecurityUtils.getCurrentUser();
        List<ScriptAssignmentResponse> response =
                assignmentService.getAssignmentsByScriptId(scriptId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/judge/{judgeId}")
    public ResponseEntity<List<ScriptAssignmentResponse>> getAssignmentsByJudgeId(@PathVariable Long judgeId) {
        User currentUser = SecurityUtils.getCurrentUser();
        List<ScriptAssignmentResponse> response =
                assignmentService.getAssignmentsByJudgeId(judgeId, currentUser);
        return ResponseEntity.ok(response);
    }
}
