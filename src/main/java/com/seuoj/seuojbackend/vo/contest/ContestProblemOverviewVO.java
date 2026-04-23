package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ContestProblemOverviewVO {

    @JsonProperty("sort_order")
    private Integer sortOrder;

    private String pid;

    private String title;
}
