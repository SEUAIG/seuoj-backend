package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ContestCreateVO {

    @JsonProperty("contest_id")
    private Long contestId;
}
