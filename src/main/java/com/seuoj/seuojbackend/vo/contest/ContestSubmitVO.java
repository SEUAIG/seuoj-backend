package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ContestSubmitVO {

    @JsonProperty("submission_no")
    private String submissionNo;
}
