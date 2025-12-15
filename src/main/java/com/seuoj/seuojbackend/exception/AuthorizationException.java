package com.seuoj.seuojbackend.exception;

import com.seuoj.seuojbackend.common.ErrorCode;
import lombok.Getter;

/**
 * 权限认证异常
 * 当用户没有足够权限访问某个接口时抛出此异常，对应HTTP状态码401
 */
@Getter
public class AuthorizationException extends RuntimeException {

    private final int code;

    public AuthorizationException(String message) {
        super(message);
        this.code = ErrorCode.AUTH_ERROR.getCode();
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.AUTH_ERROR.getCode();
    }

    public AuthorizationException(int code, String message) {
        super(message);
        this.code = code;
    }
} 