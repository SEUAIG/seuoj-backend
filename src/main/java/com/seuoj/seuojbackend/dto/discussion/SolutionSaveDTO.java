package com.seuoj.seuojbackend.dto.discussion;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建 / 修改用户题解。新建时 pid 必填，contest_id 表示从哪场比赛进来（只用来判断能不能看题、记录来源）；
 * 修改时这两项忽略。status 为 DRAFT 或 PUBLISHED。
 */
public record SolutionSaveDTO(
        @Size(max = 64) String pid,
        @JsonProperty("contest_id") Long contestId,
        @NotBlank(message = "标题不能为空") @Size(max = 120, message = "标题最多 120 字") String title,
        @Size(max = 100000, message = "正文最多 100000 字") String content,
        @NotBlank(message = "状态不能为空") String status) {
}
