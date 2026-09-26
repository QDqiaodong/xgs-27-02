package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJudgeRequest {

    @NotBlank(message = "评委姓名不能为空")
    private String name;

    private String role = "JUDGE";

    private Long userId;

    private String expertise;

    private String status = "ACTIVE";
}
