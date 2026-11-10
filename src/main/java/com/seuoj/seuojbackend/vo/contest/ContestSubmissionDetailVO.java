package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuoj.seuojbackend.model.JudgeResultDetailItem;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;

@Data
public class ContestSubmissionDetailVO {

    @JsonProperty("submission_no")
    private String submissionNo;

    private String language;

    private String status;

    private String verdict;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer score;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("result_detail")
    private List<JudgeResultDetailItem> resultDetail;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("error_detail")
    private String errorDetail;

    @JsonProperty("submit_time")
    private LocalDateTime submitTime;

    private String code;

    private String username;

    private ContestProblemOverviewVO problem;

    @JsonProperty("max_time")
    private Long maxTime;
    @JsonProperty("max_memory")
    private Long maxMemory;
    @JsonProperty("test_data_visible")
    private Boolean testDataVisible;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ProblemConfigDTO.Subtask> subtasks;
    /** 被管理者跳过：verdict 显示为 Skipped，不计入任何统计 */
    private boolean skipped;

    @JsonProperty("original_verdict")
    private String originalVerdict;

    @JsonProperty("skip_reason")
    private String skipReason;

    /** 当前用户能否 skip / 取消 skip 这条提交 */
    @JsonProperty("can_skip")
    private boolean canSkip;
}
