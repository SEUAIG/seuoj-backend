package com.seuoj.seuojbackend.interceptor;

import com.seuoj.seuojbackend.common.AuthStatus;
import lombok.Getter;

/**
 * 用户的上下文信息（保留拓展性，可以在上下文加入更多信息）
 */
@Getter
public class UserContext {
    private final Long userId;                // null 表示游客
    private final AuthStatus authStatus;      // 用户的认证状态

    private UserContext(Long userId, AuthStatus authStatus) {
        this.userId = userId;
        this.authStatus = authStatus;
    }

    public static UserContext guest() {
        return new UserContext(null, AuthStatus.ANONYMOUS);
    }

    public static UserContext of(Long userId, AuthStatus authStatus) {
        return new UserContext(userId, authStatus);
    }

    public boolean isGuest() {
        return userId == null;
    }

}
