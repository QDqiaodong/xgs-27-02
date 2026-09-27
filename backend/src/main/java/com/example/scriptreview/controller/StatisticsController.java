package com.example.scriptreview.controller;

import com.example.scriptreview.dto.response.GradeDistributionResponse;
import com.example.scriptreview.dto.response.LowScoreCommentResponse;
import com.example.scriptreview.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/grade-distribution")
    public ResponseEntity<List<GradeDistributionResponse>> getGradeDistribution() {
        List<GradeDistributionResponse> response = statisticsService.getGradeDistribution();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/low-score-comments")
    public ResponseEntity<List<LowScoreCommentResponse>> getLowScoreComments() {
        com.example.scriptreview.entity.User currentUser =
                com.example.scriptreview.util.SecurityUtils.getCurrentUser();
        List<LowScoreCommentResponse> response = statisticsService.getLowScoreComments(currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Long>> getStatusStatistics() {
        Map<String, Long> response = statisticsService.getStatusStatistics();
        return ResponseEntity.ok(response);
    }
}