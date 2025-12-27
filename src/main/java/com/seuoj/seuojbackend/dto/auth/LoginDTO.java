package com.seuoj.seuojbackend.dto.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 用户登录传输对象
 */
@Data
public class LoginDTO {
    @NotEmpty(message = "用户名不能为空")
    @Max(value = 20, message = "用户名长度不能超过20个字符")
    String username;

    @NotEmpty(message = "密码不能为空")
    @Max(value = 20, message = "密码长度不能超过20个字符")
    @Min(value = 6, message = "密码长度不能小于6个字符")
    String password;
}
