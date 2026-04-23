package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuoj.seuojbackend.model.JudgeResultDetailItem;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ContestSubmissionDetailVO {

    @JsonProperty("submission_no")
    private String submissionNo;

    private String language;

    private String status;

    private String verdict;

    private Integer score;

    @JsonProperty("result_detail")
    private List<JudgeResultDetailItem> resultDetail;

    @JsonProperty("error_detail")
    private String errorDetail;

    @JsonProperty("submit_time")
    private LocalDateTime submitTime;

    private String code;

    private String username;

    private ContestProblemOverviewVO problem;
}
