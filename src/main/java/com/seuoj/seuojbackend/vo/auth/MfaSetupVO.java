package com.seuoj.seuojbackend.vo.auth;

import lombok.Data;

/**
 * 绑定验证器：二维码内容和手动输入用的密钥
 */
@Data
public class MfaSetupVO {
    String secret;
    String otpauthUri;
}
