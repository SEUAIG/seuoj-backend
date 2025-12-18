package com.seuoj.seuojbackend.common;

import lombok.Getter;

/**
 * 错误码
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录或登录已过期"),
    AUTH_ERROR(40101, "认证失败"),
    FORBIDDEN_ERROR(40300, "无权限访问"),
    JUDGE_AUTH_ERROR(40301, "测评服务身份认证失败"),
    NOT_FOUND_ERROR(40400, "请求资源不存在"),
    CONFLICT_ERROR(40900, "业务冲突"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    THIRD_PARTY_ERROR(50200, "第三方服务异常");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
