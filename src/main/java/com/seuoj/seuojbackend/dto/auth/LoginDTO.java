package com.seuoj.seuojbackend.dto.auth;

import lombok.Data;

/**
 * 用户登录传输对象
 */
@Data
public class LoginDTO {
    String username;
    String password;
}
