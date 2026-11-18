package com.seuoj.seuojbackend.dto.discussion;

import jakarta.validation.constraints.Size;

/** 管理者精选 / 隐藏题解；为 null 的字段不改 */
public record PostModerationDTO(
        Boolean featured,
        Boolean hidden,
        @Size(max = 200, message = "原因最多 200 字") String reason) {
}
