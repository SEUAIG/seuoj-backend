package com.seuoj.seuojbackend.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ContestCreateDTO {

    @NotBlank(message = "title 不能为空")
    private String title;

    private String subtitle;

    private String description;

    @NotNull(message = "start_time 不能为空")
    @JsonProperty("start_time")
    private LocalDateTime startTime;

    @NotNull(message = "end_time 不能为空")
    @JsonProperty("end_time")
    private LocalDateTime endTime;

    @NotBlank(message = "rule_type 不能为空")
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
