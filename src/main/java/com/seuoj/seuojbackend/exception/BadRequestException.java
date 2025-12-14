package com.seuoj.seuojbackend.exception;

import com.seuoj.seuojbackend.common.ErrorCode;
import lombok.Getter;

/**
 * 请求参数错误异常
 * 当请求参数不合法或缺失时抛出此异常，对应HTTP 400状态码
 */
@Getter
public class BadRequestException extends RuntimeException {

    private final int code;

    public BadRequestException(String message) {
        super(message);
        this.code = ErrorCode.PARAMS_ERROR.getCode();
    }
    
    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.PARAMS_ERROR.getCode();
    }

    public BadRequestException(int code, String message) {
        super(message);
        this.code = code;
    }
}