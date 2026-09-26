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
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 100, message = "用户名长度为3-100个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度为6-100个字符")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 100, message = "姓名不能超过100个字符")
    private String name;

    @Pattern(regexp = "^(ADMIN|JUDGE|VIEWER)$", message = "角色必须为ADMIN/JUDGE/VIEWER")
    private String role;

    @Email(message = "邮箱格式不正确")
    @Size(max = 200, message = "邮箱不能超过200个字符")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}