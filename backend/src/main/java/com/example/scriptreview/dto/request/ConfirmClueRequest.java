package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ConfirmClueRequest {

    /** IDENTITY-确认属于投稿身份；CHARACTER-确认属于正文剧情人物 */
    @NotBlank(message = "核对结论不能为空")
    @Pattern(regexp = "IDENTITY|CHARACTER", message = "decision 只能是 IDENTITY 或 CHARACTER")
    private String decision;

    /** 确认属于投稿身份时可指定归属身份；缺省归属投稿人 */
    private Long identityId;
}
