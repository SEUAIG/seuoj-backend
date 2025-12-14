package com.seuoj.seuojbackend.exception;

import com.seuoj.seuojbackend.common.ErrorCode;
import lombok.Getter;

/**
 * 资源未找到异常
 * 当请求的资源不存在时抛出此异常，对应HTTP 404状态码
 */
@Getter
public class NotFoundException extends RuntimeException {

    private final int code;

    public NotFoundException(String message) {
        super(message);
        this.code = ErrorCode.NOT_FOUND_ERROR.getCode();
    }
    
    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.NOT_FOUND_ERROR.getCode();
    }

    public NotFoundException(int code, String message) {
        super(message);
        this.code = code;
    }
}