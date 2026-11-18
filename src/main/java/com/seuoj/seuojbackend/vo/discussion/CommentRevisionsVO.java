package com.seuoj.seuojbackend.vo.discussion;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/** 一条发言的全部版本，从第 1 版到当前版；started_at 是这一版开始生效的时间 */
public record CommentRevisionsVO(
        @JsonProperty("comment_id") Long commentId,
        List<Version> versions) {

    public record Version(
            int version,
            String content,
            @JsonProperty("started_at") LocalDateTime startedAt,
            boolean current) {
    }
}
