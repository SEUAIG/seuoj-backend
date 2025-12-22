package com.seuoj.seuojbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户注册DTO
 */
@Data
public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
