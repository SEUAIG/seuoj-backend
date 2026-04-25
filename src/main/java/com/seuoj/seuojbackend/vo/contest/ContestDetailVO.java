package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ContestDetailVO {

    private String title;

    private String subtitle;

    private String description;

    @JsonProperty("start_time")
    private LocalDateTime startTime;

    @JsonProperty("end_time")
    private LocalDateTime endTime;

    private String status;

    @JsonProperty("is_registered")
    private Boolean isRegistered;

    @JsonProperty("rule_type")
    private String ruleType;

    @JsonProperty("is_public")
    private Boolean isPublic;

    @JsonProperty("hide_statistics")
    private Boolean hideStatistics;

    @JsonProperty("problem_list")
    private List<ContestProblemOverviewVO> problemList;

    @JsonProperty("can_write")
    private Boolean canWrite;
}
