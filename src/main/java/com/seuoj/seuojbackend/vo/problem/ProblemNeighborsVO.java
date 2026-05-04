package com.seuoj.seuojbackend.vo.problem;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ProblemNeighborsVO {
    @JsonProperty("prev_pid")
    private String prevPid;

    @JsonProperty("next_pid")
    private String nextPid;
}
