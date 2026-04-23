package com.seuoj.seuojbackend.vo.problemset;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 创建题单返回
 */
@Data
public class ProblemSetCreateVO {
    /**
     * 题单 ID
     */
    @JsonProperty("problem_set_id")
    private Long problemSetId;
}
