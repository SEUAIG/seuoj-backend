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

    private String verdict;

    private Integer score;

    @JsonProperty("submit_time")
    private LocalDateTime submitTime;

    private String username;

    private String nickname;

    private ContestProblemOverviewVO problem;
}
