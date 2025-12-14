package com.seuoj.seuojbackend.exception;

import com.seuoj.seuojbackend.common.ErrorCode;
import lombok.Getter;

/**
 * 系统内部异常
 * 当发生非预期的系统错误时抛出此异常，对应HTTP 500状态码
 */
@Getter
public class InternalServerException extends RuntimeException {

    private final int code;

    public InternalServerException(String message) {
        super(message);
        this.code = ErrorCode.SYSTEM_ERROR.getCode();
    }

    public InternalServerException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.SYSTEM_ERROR.getCode();
    }

    public InternalServerException(int code, String message) {
        super(message);
        this.code = code;
    }
}
