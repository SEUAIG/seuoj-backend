package com.seuoj.seuojbackend.vo.problemset;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 创建题单返回
 */
@Data
public class ProblemSetCreateVO {
    /**
     * 题单公开 ID
     */
    @JsonProperty("problem_set_public_id")
    private String problemSetPublicId;
}
