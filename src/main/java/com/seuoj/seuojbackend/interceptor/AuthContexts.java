package com.seuoj.seuojbackend.interceptor;

import com.seuoj.seuojbackend.exception.AuthorizationException;

/**
 * 用户上下文获取校验类
 */
public class AuthContexts {

    private static final String DEFAULT_LOGIN_REQUIRED_MESSAGE = "用户未登录";

    private AuthContexts() {
    }

    public static Long requiredUserId() {
        return requiredUserContext(DEFAULT_LOGIN_REQUIRED_MESSAGE).getUserId();
    }

    public static Long userIdOrNull() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.isGuest()) {
            return null;
        }
        return ctx.getUserId();
    }

    public static UserContext requiredUserContext(String message) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.isGuest()) {
            throw new AuthorizationException(message);
        }
        return ctx;
    }
}
