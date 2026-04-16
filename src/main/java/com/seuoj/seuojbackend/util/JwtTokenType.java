package com.seuoj.seuojbackend.util;

import lombok.Getter;

@Getter
public enum JwtTokenType {
    ACCESS("access"),
    TEMP("temp");

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
