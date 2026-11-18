package com.seuoj.seuojbackend.vo.discussion;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** 题解详情页。hidden_reason 只给作者和管理者。 */
public record PostDetailVO(
        Long id,
        String kind,
        String title,
        String content,
        CommentVO.Author author,
        String status,
        boolean featured,
        boolean hidden,
        @JsonProperty("hidden_reason") String hiddenReason,
        int score,
        @JsonProperty("my_vote") int myVote,
        @JsonProperty("comment_count") long commentCount,
        @JsonProperty("allow_comment") boolean allowComment,
        boolean mine,
        @JsonProperty("can_manage") boolean canManage,
        String pid,
        @JsonProperty("problem_title") String problemTitle,
        /* 从哪场比赛进来：请求带的 contest_id，用来链回比赛题目页 */
        @JsonProperty("contest_id") Long contestId,
        @JsonProperty("published_at") LocalDateTime publishedAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt) {
}
