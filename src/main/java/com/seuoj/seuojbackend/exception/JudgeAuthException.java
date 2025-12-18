package com.seuoj.seuojbackend.exception;

import com.seuoj.seuojbackend.common.ErrorCode;
import lombok.Getter;

@Getter
public class JudgeAuthException extends RuntimeException {
    private final int code;

    public JudgeAuthException(String message) {
        super(message);
        this.code = ErrorCode.JUDGE_AUTH_ERROR.getCode();
    }

    public JudgeAuthException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.JUDGE_AUTH_ERROR.getCode();
    }

    public JudgeAuthException(int code, String message) {
        super(message);
        this.code = code;
    }
}
