package com.example.scriptreview.controller;

import com.example.scriptreview.dto.request.CreateScoreRequest;
import com.example.scriptreview.dto.response.ScoreResponse;
import com.example.scriptreview.entity.User;
import com.example.scriptreview.service.GradeService;
import com.example.scriptreview.service.ScoreService;
import com.example.scriptreview.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;
    private final GradeService gradeService;

    @PostMapping
    public ResponseEntity<ScoreResponse> createScore(@Valid @RequestBody CreateScoreRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();
        ScoreResponse response = scoreService.createScore(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/script/{scriptId}")
    public ResponseEntity<List<ScoreResponse>> getScoresByScriptId(@PathVariable Long scriptId) {
        User currentUser = SecurityUtils.getCurrentUser();
        List<ScoreResponse> response = scoreService.getScoresByScriptId(scriptId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/judge/{judgeId}")
    public ResponseEntity<List<ScoreResponse>> getScoresByJudgeId(@PathVariable Long judgeId) {
        User currentUser = SecurityUtils.getCurrentUser();
        List<ScoreResponse> response = scoreService.getScoresByJudgeId(judgeId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/grade/{scriptId}")
    public ResponseEntity<Map<String, Object>> calculateGrade(@PathVariable Long scriptId) {
        Map<String, Object> response = gradeService.calculateGrade(scriptId);
        return ResponseEntity.ok(response);
    }
}
