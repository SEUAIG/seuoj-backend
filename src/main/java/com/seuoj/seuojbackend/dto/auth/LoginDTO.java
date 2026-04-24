package com.seuoj.seuojbackend.dto.auth;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {
    @NotEmpty(message = "用户名或邮箱不能为空")
    @Size(max = 128, message = "用户名或邮箱长度不能超过128个字符")
    private String identifier;

    @NotEmpty(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20个字符之间")
    private String password;
}
