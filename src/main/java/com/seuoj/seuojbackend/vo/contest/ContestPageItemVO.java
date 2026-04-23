package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ContestPageItemVO {

    @JsonProperty("contest_id")
    private Long contestId;

    private String title;

    private String subtitle;

    private String description;

    @JsonProperty("start_time")
    private LocalDateTime startTime;

    @JsonProperty("end_time")
    private LocalDateTime endTime;

    private String status;

    @JsonProperty("rule_type")
    private String ruleType;

    @JsonProperty("is_public")
    private Boolean isPublic;
}
