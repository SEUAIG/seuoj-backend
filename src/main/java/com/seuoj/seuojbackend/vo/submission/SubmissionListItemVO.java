package com.seuoj.seuojbackend.vo.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubmissionListItemVO {
    @JsonProperty("submission_no")
    private String submissionNo;
    private String pid;

    @JsonProperty("assignment_id")
    private Long assignmentId;
    private String language;
    private String status;
    /** 被跳过时为 Skipped */
    private String verdict;

    /** 跳过前的原始结果 */
    @JsonProperty("original_verdict")
    private String originalVerdict;

    private boolean skipped;
    @JsonProperty("submit_time")
    private LocalDateTime submitTime;
    @JsonProperty("finish_time")
    private LocalDateTime finishTime;
    @JsonProperty("user_id")
    private Long userId;
    private String username;
    private String nickname;
}
