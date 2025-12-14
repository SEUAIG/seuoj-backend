package com.seuoj.seuojbackend.exception;

import com.seuoj.seuojbackend.common.ErrorCode;
import lombok.Getter;

/**
 * 业务冲突异常
 * 当业务逻辑发生冲突时抛出此异常，对应HTTP 409状态码
 * 例如：重复创建、状态冲突等
 */
@Getter
public class ConflictException extends RuntimeException {

    private final int code;

    public ConflictException(String message) {
        super(message);
        this.code = ErrorCode.CONFLICT_ERROR.getCode();
    }
    
    public ConflictException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.CONFLICT_ERROR.getCode();
    }

    public ConflictException(int code, String message) {
        super(message);
        this.code = code;
    }
}