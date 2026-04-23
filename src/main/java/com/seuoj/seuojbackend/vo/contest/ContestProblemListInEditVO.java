package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class ContestProblemListInEditVO {

    @JsonProperty("problem_list")
    private List<ContestProblemOverviewVO> problemList;
}
