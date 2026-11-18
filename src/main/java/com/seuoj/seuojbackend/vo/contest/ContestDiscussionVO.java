package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/** 比赛「讨论」标签：官方题解 + 题目索引 */
public record ContestDiscussionVO(
        @JsonProperty("discussion_enabled") boolean discussionEnabled,
        boolean ended,
        @JsonProperty("can_manage") boolean canManage,
        Editorial editorial,
        List<ProblemEntry> problems,
        /* 整场讨论的发言数（未删除、未隐藏，含回复） */
        @JsonProperty("comment_count") long commentCount) {

    /**
     * 官方题解。非管理者只在已发布且到了公开时间后才拿得到；
     * 管理者总能拿到，visible 表示此刻非管理者是否看得到。
     */
    public record Editorial(
            @JsonProperty("post_id") Long postId,
            String title,
            String content,
            String status,
            @JsonProperty("visible_after") LocalDateTime visibleAfter,
            boolean visible,
            @JsonProperty("author_name") String authorName,
            @JsonProperty("published_at") LocalDateTime publishedAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt,
            List<Attachment> attachments) {
    }

    public record Attachment(
            Long id,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("file_size") Long fileSize) {
    }

    public record ProblemEntry(
            @JsonProperty("sort_order") Integer sortOrder,
            String pid,
            String title,
            /* 这道题的讨论发言数（未删除、未隐藏，含回复） */
            @JsonProperty("comment_count") long commentCount,
            /* 这道题公开的用户题解数（已发布、未隐藏） */
            @JsonProperty("solution_count") long solutionCount) {
    }
}
