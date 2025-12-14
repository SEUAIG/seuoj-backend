package com.seuoj.seuojbackend.common;

/**
 * 用户身份校验状态枚举
 */
public enum AuthStatus {
    ANONYMOUS,      // 没有 token
    AUTHENTICATED,  // token 合法
    INVALID_TOKEN   // token 存在但非法
}
