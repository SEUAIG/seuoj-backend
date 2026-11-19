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
    /**
     * 非空表示还差两步验证：verify = 输入动态码，setup = 先绑定验证器。此时 jwt 为空
     */
    String mfa;
    String mfaToken;
    /**
     * 刚启用两步验证时返回的一次性恢复码明文，只出现这一次
     */
    java.util.List<String> recoveryCodes;
}
