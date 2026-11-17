package com.seuoj.seuojbackend.vo.discussion;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * 一条发言。deleted / 对当前用户隐藏时 content、author 为 null，前端显示占位。
 * hidden_reason 只给作者和管理者。
 */
public record CommentVO(
        Long id,
        @JsonProperty("parent_id") Long parentId,
        @JsonProperty("root_id") Long rootId,
        int depth,
        Author author,
        String content,
        boolean deleted,
        boolean hidden,
        @JsonProperty("hidden_reason") String hiddenReason,
        int score,
        @JsonProperty("my_vote") int myVote,
        @JsonProperty("reply_count") int replyCount,
        boolean pinned,
        boolean mine,
        @JsonProperty("can_delete") boolean canDelete,
        @JsonProperty("created_at") LocalDateTime createdAt) {

    public record Author(
            Long id,
            String username,
            String nickname) {
    }
}
