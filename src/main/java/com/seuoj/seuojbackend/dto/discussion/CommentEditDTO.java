package com.seuoj.seuojbackend.dto.discussion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 修改发言：新正文 */
public record CommentEditDTO(
        @NotBlank(message = "发言不能为空") @Size(max = 2000, message = "发言最多 2000 字") String content) {
}
