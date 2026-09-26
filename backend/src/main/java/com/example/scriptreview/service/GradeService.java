package com.example.scriptreview.service;

import com.example.scriptreview.entity.DimensionWeight;
import com.example.scriptreview.entity.Score;
import com.example.scriptreview.entity.Script;
import com.example.scriptreview.repository.DimensionWeightRepository;
import com.example.scriptreview.repository.ScoreRepository;
import com.example.scriptreview.repository.ScriptRepository;
import com.example.scriptreview.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GradeService {

    private final ScoreRepository scoreRepository;
    private final DimensionWeightRepository dimensionWeightRepository;
    private final ScriptRepository scriptRepository;

    /**
     * 分级计算。与回避审批/替补打分共用同一把脚本行锁：
     * 批准回避（旧评分置无效）、替补评分、分级同时发生时，最终等级只使用
     * “当时有效且满足人数要求”的评分。
     */
    @Transactional
    public Map<String, Object> calculateGrade(Long scriptId) {
        Script script = scriptRepository.findByIdForUpdate(scriptId)
                .orElseThrow(() -> new RuntimeException("脚本不存在"));

        // 锁定该脚本全部评分行，避免计算期间有评分被回避流程置为无效
        List<Score> allScores = scoreRepository.findByScriptIdForUpdate(scriptId);
        List<Score> scores = allScores.stream()
                .filter(s -> Boolean.TRUE.equals(s.getValid()))
                .collect(Collectors.toList());

        if (scores.size() < ScriptStatusService.REQUIRED_VALID_SCORES) {
            throw new ConflictException("有效评分不足" + ScriptStatusService.REQUIRED_VALID_SCORES
                    + "位，暂不能分级（脚本已进入待补评状态）");
        }

        List<DimensionWeight> weights = dimensionWeightRepository.findAll();
        Map<String, Double> weightMap = weights.stream()
                .collect(Collectors.toMap(DimensionWeight::getDimensionName, DimensionWeight::getWeight));

        // 极端分识别只在当前有效评分上进行；已失效评分先清除极端标记
        allScores.forEach(score -> {
            if (Boolean.FALSE.equals(score.getValid()) && Boolean.TRUE.equals(score.getIsExtreme())) {
                score.setIsExtreme(false);
            }
        });
        resetExtremeFlags(scores);

        Map<String, Double> dimensionAverages = calculateDimensionAverages(scores);

        scoreRepository.saveAll(allScores);

        double totalScore = 0;
        for (Map.Entry<String, Double> entry : dimensionAverages.entrySet()) {
            double weight = weightMap.getOrDefault(entry.getKey(), 0.2);
            totalScore += entry.getValue() * 10 * weight;
        }

        String grade = determineGrade(totalScore);
        double rounded = Math.round(totalScore * 100.0) / 100.0;

        script.setGrade(grade);
        script.setTotalScore(rounded);
        script.setStatus("GRADED");
        scriptRepository.save(script);

        Map<String, Object> result = new HashMap<>();
        result.put("scriptId", scriptId);
        result.put("totalScore", rounded);
        result.put("grade", grade);
        result.put("validScoreCount", scores.size());
        result.put("excludedScoreCount", allScores.size() - scores.size());
        result.put("dimensionAverages", dimensionAverages);

        return result;
    }

    private void resetExtremeFlags(List<Score> scores) {
        scores.forEach(score -> score.setIsExtreme(false));
    }

    private Map<String, Double> calculateDimensionAverages(List<Score> scores) {
        Map<String, Double> averages = new HashMap<>();

        List<Integer> creativityScores = new ArrayList<>();
        List<Integer> commercialScores = new ArrayList<>();
        List<Integer> difficultyScores = new ArrayList<>();
        List<Integer> logicScores = new ArrayList<>();
        List<Integer> audienceScores = new ArrayList<>();

        for (Score score : scores) {
            creativityScores.add(score.getCreativityScore());
            commercialScores.add(score.getCommercialScore());
            difficultyScores.add(score.getDifficultyScore());
            logicScores.add(score.getLogicScore());
            audienceScores.add(score.getAudienceScore());
        }

        averages.put("creativity", calculateFilteredAverage(scores, creativityScores, "creativity"));
        averages.put("commercial", calculateFilteredAverage(scores, commercialScores, "commercial"));
        averages.put("difficulty", calculateFilteredAverage(scores, difficultyScores, "difficulty"));
        averages.put("logic", calculateFilteredAverage(scores, logicScores, "logic"));
        averages.put("audience", calculateFilteredAverage(scores, audienceScores, "audience"));

        return averages;
    }

    private double calculateFilteredAverage(List<Score> scores, List<Integer> scoreValues, String dimension) {
        if (scoreValues == null || scoreValues.isEmpty()) {
            return 0;
        }

        if (scoreValues.size() < 3) {
            return scoreValues.stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        Integer min = Collections.min(scoreValues);
        Integer max = Collections.max(scoreValues);

        List<Integer> filteredScores = new ArrayList<>(scoreValues);
        boolean removedMin = filteredScores.remove(min);

        Set<Integer> minIndices = new HashSet<>();
        Set<Integer> maxIndices = new HashSet<>();

        for (int i = 0; i < scoreValues.size(); i++) {
            if (scoreValues.get(i).equals(min)) {
                minIndices.add(i);
            }
            if (scoreValues.get(i).equals(max)) {
                maxIndices.add(i);
            }
        }

        if (removedMin) {
            minIndices.forEach(idx -> markScoreExtreme(scores, idx, dimension));

            if (filteredScores.size() >= 2 && !min.equals(max)) {
                filteredScores.remove(max);
                maxIndices.forEach(idx -> {
                    if (!minIndices.contains(idx)) {
                        markScoreExtreme(scores, idx, dimension);
                    }
                });
            }
        }

        if (filteredScores.isEmpty()) {
            return scoreValues.stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        return filteredScores.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private void markScoreExtreme(List<Score> scores, int index, String dimension) {
        scores.get(index).setIsExtreme(true);
    }

    private String determineGrade(double totalScore) {
        if (totalScore >= 90) {
            return "S";
        } else if (totalScore >= 75) {
            return "A";
        } else if (totalScore >= 60) {
            return "B";
        } else {
            return "C";
        }
    }
}
