package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ContestStandingsScoreDetailVO {

    private Integer score;

    @JsonProperty("judge_id")
    private Long judgeId;

    private Boolean accepted;

    @JsonProperty("unacceptedCount")
    private Integer unacceptedCount;

    @JsonProperty("acceptedTime")
    private Long acceptedTime;

    @JsonProperty("weighted_score")
    private Integer weightedScore;
}
