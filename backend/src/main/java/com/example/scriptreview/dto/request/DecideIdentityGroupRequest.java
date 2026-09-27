package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员对“待核对”身份分组的核对结论。
 * 核对只调整脱敏视图与代号映射（CONFIRMED_IDENTITY 替换为代号 /
 * CONFIRMED_CHARACTER 保留原文），不改写原稿。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecideIdentityGroupRequest {

    @NotNull(message = "核对结论不能为空")
    @Pattern(regexp = "CONFIRMED_IDENTITY|CONFIRMED_CHARACTER",
            message = "核对结论只能是 CONFIRMED_IDENTITY 或 CONFIRMED_CHARACTER")
    private String decision;
}
