package com.seuoj.seuojbackend.dto.auth;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户登录传输对象
 */
@Data
public class LoginDTO {
    @NotEmpty(message = "用户名不能为空")
    @Size(max = 20, message = "用户名长度不能超过20个字符")
    String username;

    @NotEmpty(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20个字符之间")
    String password;
}
