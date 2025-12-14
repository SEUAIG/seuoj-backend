package com.seuoj.seuojbackend.dto.auth;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 用户登录传输对象
 */
@Data
public class LoginDTO {
    @NotEmpty(message = "用户名不能为空")
    String username;

    @NotEmpty(message = "密码不能为空")
    String password;
}
