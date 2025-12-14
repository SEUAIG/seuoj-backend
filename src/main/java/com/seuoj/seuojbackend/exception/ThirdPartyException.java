package com.seuoj.seuojbackend.exception;

import com.seuoj.seuojbackend.common.ErrorCode;
import lombok.Getter;

/**
 * 第三方服务异常
 * 用于调用外部服务（如OAuth2、短信服务等）失败时抛出的异常
 */
@Getter
public class ThirdPartyException extends RuntimeException {

    private final int code;

    public ThirdPartyException(String message) {
        super(message);
        this.code = ErrorCode.THIRD_PARTY_ERROR.getCode();
    }

    public ThirdPartyException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.THIRD_PARTY_ERROR.getCode();
    }

    public ThirdPartyException(int code, String message) {
        super(message);
        this.code = code;
    }
}
