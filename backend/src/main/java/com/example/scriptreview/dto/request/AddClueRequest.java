package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddClueRequest {

    @NotBlank(message = "线索类型不能为空")
    @Pattern(regexp = "NAME|PHONE|EMAIL|ORG|SOCIAL", message = "非法的线索类型")
    private String clueType;

    @NotBlank(message = "字段来源不能为空")
    @Pattern(regexp = "AUTHOR|SOURCE|TITLE|DESCRIPTION|ATTACHMENT", message = "非法的字段来源")
    private String fieldOrigin;

    @NotBlank(message = "线索原文不能为空")
    @Size(max = 191, message = "线索原文不能超过191个字符")
    private String originalText;

    /** 归属身份；缺省归属投稿人 */
    private Long identityId;
}
