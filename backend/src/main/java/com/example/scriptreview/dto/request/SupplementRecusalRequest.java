package com.example.scriptreview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplementRecusalRequest {

    @NotBlank(message = "补充材料不能为空")
    @Size(max = 2000, message = "补充材料不能超过2000个字符")
    private String supplementInfo;
}
