package com.seuoj.seuojbackend.exception;

import com.seuoj.seuojbackend.common.ErrorCode;
import lombok.Getter;

/**
 * 权限不足异常
 * 当用户有权限访问系统但没有权限执行特定操作时抛出此异常，对应HTTP 403状态码
 * 与AuthorizationException(401)的区别：
 * - 401: 未认证或认证失败
 * - 403: 已认证但权限不足
 */
@Getter
public class ForbiddenException extends RuntimeException {

    private final int code;

    public ForbiddenException(String message) {
        super(message);
        this.code = ErrorCode.FORBIDDEN_ERROR.getCode();
    }
    
    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.FORBIDDEN_ERROR.getCode();
    }

    public ForbiddenException(int code, String message) {
        super(message);
        this.code = code;
    }
}