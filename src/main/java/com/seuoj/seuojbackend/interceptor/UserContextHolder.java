package com.seuoj.seuojbackend.interceptor;

import org.apache.catalina.User;

/**
 * 用户上下文持有者
 */
public class UserContextHolder {
    private static final ThreadLocal<UserContext>  userContext = new ThreadLocal<>();

    public static void set(UserContext context) {
        userContext.set(context);
    }

    public static UserContext get(){
        return userContext.get();
    }

    public static void clear(){
        userContext.remove();
    }
}
