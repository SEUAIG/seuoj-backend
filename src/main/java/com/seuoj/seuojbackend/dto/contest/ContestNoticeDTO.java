package com.seuoj.seuojbackend.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 比赛公告与答疑的请求体 */
public final class ContestNoticeDTO {

    private ContestNoticeDTO() {
    }

    public record Announcement(
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题最多 200 字") String title,
            @Size(max = 10000, message = "正文最多 10000 字") String content,
            @JsonProperty("is_pinned") Boolean isPinned) {
    }

    public record Ask(
            @JsonProperty("problem_pid") @Size(max = 64) String problemPid,
            @NotBlank(message = "问题不能为空") @Size(max = 2000, message = "问题最多 2000 字") String question) {
    }

    public record Answer(
            @NotBlank(message = "回复不能为空") @Size(max = 4000, message = "回复最多 4000 字") String answer,
            @JsonProperty("is_public") Boolean isPublic) {
    }
}
