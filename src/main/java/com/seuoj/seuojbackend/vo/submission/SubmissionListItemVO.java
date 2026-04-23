package com.seuoj.seuojbackend.vo.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubmissionListItemVO {
    @JsonProperty("submission_no")
    private String submissionNo;
    private String pid;
    private String language;
    private String status;
    private String verdict;
    @JsonProperty("submit_time")
    private LocalDateTime submitTime;
    @JsonProperty("finish_time")
    private LocalDateTime finishTime;
    @JsonProperty("user_id")
    private Long userId;
    private String username;
    private String nickname;
}
