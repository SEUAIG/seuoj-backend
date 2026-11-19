package com.seuoj.seuojbackend.dto.auth;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MfaCodeDTO {
    @NotEmpty(message = "缺少验证令牌")
    private String mfaToken;

    @NotEmpty(message = "请输入验证码")
    @Size(max = 32, message = "验证码过长")
    private String code;
}
