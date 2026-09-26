package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRecusalRequest {

    @NotNull(message = "脚本ID不能为空")
    private Long scriptId;

    @NotNull(message = "评委ID不能为空")
    private Long judgeId;

    @NotBlank(message = "回避类型不能为空")
    private String type;

    @NotBlank(message = "回避说明不能为空")
    @Size(max = 2000, message = "回避说明不能超过2000个字符")
    private String reason;

    @NotBlank(message = "可核验依据不能为空")
    @Size(max = 2000, message = "可核验依据不能超过2000个字符")
    private String evidence;
}
