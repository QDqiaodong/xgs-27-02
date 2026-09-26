package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubstituteJudgeRequest {

    @NotNull(message = "替补评委ID不能为空")
    private Long substituteJudgeId;

    private LocalDateTime deadline;
}
