package com.seuoj.seuojbackend.vo.auth;

import lombok.Data;

/**
 * 用户登录返回对象
 */
@Data
public class LoginVO {
    Long id;
    java.util.Set<com.seuoj.seuojbackend.common.Capability> capabilities;
    String jwt;
    String username;
    String nickname;
    String role;
}
