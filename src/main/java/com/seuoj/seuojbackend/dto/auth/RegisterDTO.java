package com.seuoj.seuojbackend.dto.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户注册DTO
 */
@Data
public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    @Max(value = 20, message = "用户名长度不能超过20个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Max(value = 20, message = "密码长度不能超过20个字符")
    @Min(value = 6, message = "密码长度不能小于6个字符")
    private String password;
}
