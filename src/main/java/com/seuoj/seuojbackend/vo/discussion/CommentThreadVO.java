package com.seuoj.seuojbackend.vo.discussion;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 一页讨论。comments 是扁平列表：本页的顶层发言加上它们的回复（每棵树最多 200 条），前端按 parent_id 拼树。
 * 子树页（继续这个讨论）里 focus_id 是根，comments 只含它和它的后代。
 */
public record CommentThreadVO(
        @JsonProperty("target_type") String targetType,
        String pid,
        @JsonProperty("problem_title") String problemTitle,
        @JsonProperty("contest_id") Long contestId,
        /* 题解评论区：题解 id 和标题，用来链回题解 */
        @JsonProperty("post_id") Long postId,
        @JsonProperty("post_title") String postTitle,
        @JsonProperty("can_post") boolean canPost,
        @JsonProperty("can_manage") boolean canManage,
        String sort,
        long current,
        long size,
        long total,
        @JsonProperty("focus_id") Long focusId,
        List<CommentVO> pinned,
        List<CommentVO> comments) {
}
