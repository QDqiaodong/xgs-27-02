package com.example.scriptreview.controller;

import com.example.scriptreview.dto.request.CreateJudgeRequest;
import com.example.scriptreview.dto.response.JudgeResponse;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.service.JudgeService;
import com.example.scriptreview.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/judges")
@RequiredArgsConstructor
public class JudgeController {

    private final JudgeService judgeService;

    @PostMapping
    public ResponseEntity<JudgeResponse> createJudge(@Valid @RequestBody CreateJudgeRequest request) {
        JudgeResponse response = judgeService.createJudge(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<JudgeResponse>> getJudges(
            @RequestParam(required = false) String role) {
        List<JudgeResponse> response;
        if (role != null && !role.isEmpty()) {
            response = judgeService.getJudgesByRole(role);
        } else {
            response = judgeService.getAllJudges();
        }
        return ResponseEntity.ok(response);
    }

    /** 当前登录评委对应的评委档案（前端用于默认选中本人） */
    @GetMapping("/me")
    public ResponseEntity<JudgeResponse> getMyJudgeProfile() {
        User currentUser = SecurityUtils.getCurrentUser();
        JudgeResponse response = judgeService.getJudgeByUserId(currentUser.getId());
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JudgeResponse> getJudgeById(@PathVariable Long id) {
        JudgeResponse response = judgeService.getJudgeById(id);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}
