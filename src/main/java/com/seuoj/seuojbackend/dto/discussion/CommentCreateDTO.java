package com.seuoj.seuojbackend.dto.discussion;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发言。parent_id 非空时是回复，挂在父发言所在的 thread 上，其余定位字段忽略；
 * 否则按 target_type 定位：PROBLEM 用 pid，CONTEST 用 contest_id，POST 用 post_id。
 */
public record CommentCreateDTO(
        @JsonProperty("target_type") String targetType,
        @Size(max = 64) String pid,
        @JsonProperty("contest_id") Long contestId,
        @JsonProperty("post_id") Long postId,
        @JsonProperty("parent_id") Long parentId,
        @NotBlank(message = "发言不能为空") @Size(max = 2000, message = "发言最多 2000 字") String content) {
}
