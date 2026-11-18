package com.seuoj.seuojbackend.vo.discussion;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 题目页「📖 题解」「💭 讨论」入口：能不能进、各有多少 */
public record CommentSummaryVO(
        @JsonProperty("can_view") boolean canView,
        @JsonProperty("comment_count") long commentCount,
        @JsonProperty("solution_count") long solutionCount) {
}
