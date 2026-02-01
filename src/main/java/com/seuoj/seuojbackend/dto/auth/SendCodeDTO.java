package com.seuoj.seuojbackend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送验证码请求DTO
 */
@Data
public class SendCodeDTO {
    @NotEmpty(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    private String email;
}
