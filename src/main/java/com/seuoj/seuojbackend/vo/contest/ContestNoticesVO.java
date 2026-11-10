package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/** 比赛公告 + 当前用户可见的答疑，前端一次轮询拿全 */
public record ContestNoticesVO(
        List<Announcement> announcements,
        List<Clarification> clarifications,
        @JsonProperty("can_manage") boolean canManage) {

    public record Announcement(
            @JsonProperty("announcement_id") Long announcementId,
            String title,
            String content,
            @JsonProperty("is_pinned") boolean isPinned,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Clarification(
            @JsonProperty("clarification_id") Long clarificationId,
            @JsonProperty("problem_pid") String problemPid,
            String question,
            String answer,
            @JsonProperty("is_public") boolean isPublic,
            boolean mine,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("answered_at") LocalDateTime answeredAt,
            /* 以下两项只返回给比赛管理者 */
            @JsonProperty("asker_name") String askerName,
            @JsonProperty("asker_username") String askerUsername) {
    }
}
