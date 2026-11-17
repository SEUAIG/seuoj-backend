package com.seuoj.seuojbackend.dto.discussion;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 投票：value 为 1 赞、-1 踩、0 取消；重复提交同一个值没有副作用 */
public record VoteDTO(
        @JsonProperty("target_type") @NotBlank String targetType,
        @JsonProperty("target_id") @NotNull Long targetId,
        @NotNull @Min(-1) @Max(1) Integer value) {
}
