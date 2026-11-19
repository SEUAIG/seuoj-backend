package com.seuoj.seuojbackend.dto.auth;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class MfaTokenDTO {
    @NotEmpty(message = "缺少验证令牌")
    private String mfaToken;
}
