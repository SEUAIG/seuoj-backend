package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class ContestStandingsVO {

    @JsonProperty("contest_id")
    private Long contestId;

    @JsonProperty("rule_type")
    private String ruleType;

    private List<ContestProblemOverviewVO> problems;

    private List<ContestStandingsRecordVO> records;
}
