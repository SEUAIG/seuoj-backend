package com.seuoj.seuojbackend.vo.problemset;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 题单列表项 VO
 */
@Data
public class ProblemSetItemVO {
    /**
     * 题单公开 ID
     */
    @JsonProperty("problem_set_public_id")
    private String problemSetPublicId;

    /**
     * 题单标题
     */
    private String title;

    /**
     * 题单描述
     */
    private String description;

    /**
     * 是否公开
     */
    @JsonProperty("is_public")
    private Boolean isPublic;

    /**
     * 题目数量
     */
    @JsonProperty("problem_count")
    private Integer problemCount;
}
