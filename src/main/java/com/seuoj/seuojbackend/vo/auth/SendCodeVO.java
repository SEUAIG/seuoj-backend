package com.seuoj.seuojbackend.vo.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 发送验证码响应VO
 */
@Data
public class SendCodeVO {
    /**
     * 验证码有效期（秒）
     */
    @JsonProperty("expire_in")
    private Integer expireIn;

    /**
     * 距离下次允许发送的剩余秒数
     */
    @JsonProperty("next_send_in")
    private Integer nextSendIn;

    /**
     * 验证码会话ID（UUID）
     */
    @JsonProperty("verification_id")
    private String verificationId;
}
