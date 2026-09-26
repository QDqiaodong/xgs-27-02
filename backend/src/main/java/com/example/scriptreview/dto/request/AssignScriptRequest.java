package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignScriptRequest {

    @NotNull(message = "脚本ID不能为空")
    private Long scriptId;

    @NotNull(message = "评委ID列表不能为空")
    private List<Long> judgeIds;

    private String deadline;
}