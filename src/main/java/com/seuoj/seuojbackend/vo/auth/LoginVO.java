package com.seuoj.seuojbackend.vo.auth;

import lombok.Data;

/**
 * 用户登录返回对象
 */
@Data
public class LoginVO {
    String jwt;
    String username;
    String role;
}
