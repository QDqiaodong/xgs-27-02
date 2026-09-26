package com.example.scriptreview.service;

import com.example.scriptreview.dto.request.CreateJudgeRequest;
import com.example.scriptreview.dto.response.JudgeResponse;
import com.example.scriptreview.entity.Judge;
import com.example.scriptreview.repository.JudgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JudgeService {

    private final JudgeRepository judgeRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Transactional
    public JudgeResponse createJudge(CreateJudgeRequest request) {
        Judge judge = Judge.builder()
                .name(request.getName())
                .role(request.getRole() != null ? request.getRole() : "JUDGE")
                .userId(request.getUserId())
                .expertise(request.getExpertise())
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .build();
        Judge saved = judgeRepository.save(judge);
        return toResponse(saved);
    }

    public List<JudgeResponse> getAllJudges() {
        return judgeRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<JudgeResponse> getJudgesByRole(String role) {
        return judgeRepository.findByRole(role).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public JudgeResponse getJudgeById(Long id) {
        return judgeRepository.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    public JudgeResponse getJudgeByUserId(Long userId) {
        return judgeRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElse(null);
    }

    private JudgeResponse toResponse(Judge judge) {
        return JudgeResponse.builder()
                .id(judge.getId())
                .name(judge.getName())
                .role(judge.getRole())
                .userId(judge.getUserId())
                .expertise(judge.getExpertise())
                .status(judge.getStatus())
                .createdAt(judge.getCreatedAt() != null ? judge.getCreatedAt().format(FORMATTER) : null)
                .build();
    }
}
