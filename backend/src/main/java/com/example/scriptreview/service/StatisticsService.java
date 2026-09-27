package com.example.scriptreview.service;

import com.example.scriptreview.dto.response.GradeDistributionResponse;
import com.example.scriptreview.dto.response.LowScoreCommentResponse;
import com.example.scriptreview.entity.Judge;
import com.example.scriptreview.entity.Score;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.repository.JudgeRepository;
import com.example.scriptreview.repository.ScoreRepository;
import com.example.scriptreview.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final ScriptRepository scriptRepository;
    private final ScoreRepository scoreRepository;
    private final JudgeRepository judgeRepository;
    private final DesensitizationService desensitizationService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<GradeDistributionResponse> getGradeDistribution() {
        List<GradeDistributionResponse> result = new ArrayList<>();
        
        long total = scriptRepository.count();
        if (total == 0) {
            total = 1;
        }

        List<String> grades = Arrays.asList("S", "A", "B", "C");
        for (String grade : grades) {
            long count = scriptRepository.countByGrade(grade);
            double percentage = Math.round((count * 100.0 / total) * 100.0) / 100.0;
            result.add(GradeDistributionResponse.builder()
                    .grade(grade)
                    .count(count)
                    .percentage(percentage)
                    .build());
        }

        return result;
    }

    public List<LowScoreCommentResponse> getLowScoreComments(com.example.scriptreview.entity.User viewer) {
        List<Script> lowScoreScripts = scriptRepository.findByGradeIn(Arrays.asList("C", "B"));

        return lowScoreScripts.stream()
                .map(script -> {
                    List<Score> scores = scoreRepository.findByScriptId(script.getId());

                    List<LowScoreCommentResponse.CommentDetail> comments = scores.stream()
                            .filter(score -> Boolean.TRUE.equals(score.getValid()))
                            .filter(score -> score.getComment() != null && !score.getComment().isEmpty())
                            .map(score -> {
                                String judgeName = judgeRepository.findById(score.getJudgeId())
                                        .map(Judge::getName)
                                        .orElse("未知评委");
                                return LowScoreCommentResponse.CommentDetail.builder()
                                        .judgeName(judgeName)
                                        .comment(score.getComment())
                                        .createdAt(score.getCreatedAt() != null ? score.getCreatedAt().format(FORMATTER) : null)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return LowScoreCommentResponse.builder()
                            .scriptId(script.getId())
                            // 查看员可见该出口：标题同样必须是脱敏版本
                            .scriptTitle(desensitizationService.maskTitleFor(script, viewer))
                            .totalScore(script.getTotalScore())
                            .grade(script.getGrade())
                            .comments(comments)
                            .build();
                })
                .filter(item -> !item.getComments().isEmpty())
                .collect(Collectors.toList());
    }

    public Map<String, Long> getStatusStatistics() {
        return Map.of(
                "pending", scriptRepository.countByStatus("PENDING"),
                "reviewing", scriptRepository.countByStatus("REVIEWING"),
                "pendingSupplement", scriptRepository.countByStatus("PENDING_SUPPLEMENT"),
                "graded", scriptRepository.countByStatus("GRADED")
        );
    }
}