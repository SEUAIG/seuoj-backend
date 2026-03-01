package com.seuoj.seuojbackend.vo.me;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HeatmapSummaryVO {
    private Long total;
    @JsonProperty("active_days")
    private String activeDays;
}
