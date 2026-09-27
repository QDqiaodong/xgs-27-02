package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScriptRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    @NotBlank(message = "题材不能为空")
    @Size(max = 50, message = "题材不能超过50个字符")
    private String genre;

    @NotNull(message = "时长不能为空")
    @Min(value = 1, message = "时长至少为1秒")
    @Max(value = 3600, message = "时长不能超过3600秒")
    private Integer duration;

    @NotBlank(message = "目标受众不能为空")
    @Size(max = 100, message = "目标受众不能超过100个字符")
    private String targetAudience;

    @Size(max = 2000, message = "描述不能超过2000个字符")
    private String description;

    @Size(max = 100, message = "作者不能超过100个字符")
    private String author;

    @Size(max = 200, message = "来源不能超过200个字符")
    private String source;

    @Size(max = 1000, message = "联系方式不能超过1000个字符")
    private String contactInfo;

    @Size(max = 2000, message = "附件说明不能超过2000个字符")
    private String attachmentNote;
}