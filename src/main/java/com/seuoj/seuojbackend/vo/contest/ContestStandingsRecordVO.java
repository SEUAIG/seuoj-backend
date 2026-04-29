package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

@Data
public class ContestStandingsRecordVO {

    private Integer rank;

    private String username;

    private String nickname;

    private Integer score;

    private Long penalty;

    @JsonProperty("score_details")
    private Map<String, ContestStandingsScoreDetailVO> scoreDetails;
}
