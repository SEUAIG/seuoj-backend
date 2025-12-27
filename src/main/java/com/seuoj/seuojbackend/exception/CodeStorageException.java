package com.seuoj.seuojbackend.exception;

import com.seuoj.seuojbackend.common.ErrorCode;
import lombok.Getter;

/**
 * 代码存储相关异常，归类为系统内部错误
 */
@Getter
public class CodeStorageException extends RuntimeException {

    private final int code;

    public CodeStorageException(String message) {
        super(message);
        this.code = ErrorCode.CODE_STORAGE_ERROR.getCode();
    }

    public CodeStorageException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.CODE_STORAGE_ERROR.getCode();
    }
}
