package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ContestSubmissionRecordVO {

    @JsonProperty("submission_no")
    private String submissionNo;

    private String language;

    private String status;

    /** 被跳过时为 Skipped */
    private String verdict;

    /** 跳过前的原始结果 */
    @JsonProperty("original_verdict")
    private String originalVerdict;

    private boolean skipped;

    private Integer score;

    @JsonProperty("submit_time")
    private LocalDateTime submitTime;

    @JsonProperty("user_id")
    private Long userId;

    private String username;

    private String nickname;

    private ContestProblemOverviewVO problem;
}
