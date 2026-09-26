package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员处理回避申请。
 * decision: APPROVE 批准 / REJECT 驳回 / REQUEST_SUPPLEMENT 要求补充材料
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecideRecusalRequest {

    @NotBlank(message = "处理决定不能为空")
    private String decision;

    @NotBlank(message = "处理理由不能为空")
    @Size(max = 2000, message = "处理理由不能超过2000个字符")
    private String comment;
}
