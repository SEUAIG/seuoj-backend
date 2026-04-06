package com.seuoj.seuojbackend.vo.problem;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NextProblemIdVO {
    @JsonProperty("next_pid")
    private String nextPid;
}
