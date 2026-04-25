package com.seuoj.seuojbackend.vo.problemset;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 题单列表项 VO
 */
@Data
public class ProblemSetItemVO {
    /**
     * 题单 ID
     */
    @JsonProperty("problem_set_id")
    private Long problemSetId;

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

    /**
     * 当前用户是否有写权限
     */
    @JsonProperty("can_write")
    private Boolean canWrite;
}
