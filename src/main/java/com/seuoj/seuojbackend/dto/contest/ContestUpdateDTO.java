package com.seuoj.seuojbackend.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ContestUpdateDTO {

    private String title;

    private String subtitle;

    private String description;

    @JsonProperty("start_time")
    private LocalDateTime startTime;

    @JsonProperty("end_time")
    private LocalDateTime endTime;

    @JsonProperty("rule_type")
    private String ruleType;

    @JsonProperty("is_public")
    private Boolean isPublic;

    @JsonProperty("hide_statistics")
    private Boolean hideStatistics;

    @JsonProperty("scoring_config")
    private String scoringConfig;

    @JsonProperty("scoring_script")
    private String scoringScript;
}
