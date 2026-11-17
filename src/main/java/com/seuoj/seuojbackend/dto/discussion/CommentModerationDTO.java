package com.seuoj.seuojbackend.dto.discussion;

import jakarta.validation.constraints.Size;

/** 管理者置顶 / 隐藏发言；为 null 的字段不改 */
public record CommentModerationDTO(
        Boolean pinned,
        Boolean hidden,
        @Size(max = 200, message = "原因最多 200 字") String reason) {
}
