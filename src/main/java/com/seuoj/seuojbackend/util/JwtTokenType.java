package com.seuoj.seuojbackend.util;

import lombok.Getter;

@Getter
public enum JwtTokenType {
    ACCESS("access"),
    TEMP("temp"),
    /** 密码已通过、还差两步验证（或首次绑定验证器）的登录中间态 */
    MFA("mfa");

    private final String claimValue;

    JwtTokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    public static JwtTokenType fromClaimValue(String value) {
        for (JwtTokenType tokenType : values()) {
            if (tokenType.claimValue.equals(value)) {
                return tokenType;
            }
        }
        throw new IllegalArgumentException("不支持的令牌类型: " + value);
    }
}
