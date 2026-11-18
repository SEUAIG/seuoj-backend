package com.seuoj.seuojbackend.vo.discussion;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目「题解」标签。items 是公开的题解（精选在前，其余按净分）；
 * 第 1 页的 mine_pending 是我自己还没公开的（草稿、被隐藏），只有我看得到。
 */
public record SolutionPageVO(
        String pid,
        @JsonProperty("problem_title") String problemTitle,
        /* 现在能不能写题解：登录、AC 过、没超过篇数上限 */
        @JsonProperty("can_post") boolean canPost,
        @JsonProperty("has_accepted") boolean hasAccepted,
        @JsonProperty("can_manage") boolean canManage,
        @JsonProperty("my_count") long myCount,
        @JsonProperty("max_per_problem") int maxPerProblem,
        long current,
        long size,
        long total,
        @JsonProperty("mine_pending") List<Item> minePending,
        List<Item> items) {

    public record Item(
            Long id,
            String title,
            CommentVO.Author author,
            int score,
            @JsonProperty("comment_count") long commentCount,
            boolean featured,
            String status,
            boolean hidden,
            boolean mine,
            @JsonProperty("published_at") LocalDateTime publishedAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt) {
    }
}
