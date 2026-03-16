package com.seuoj.seuojbackend.vo.problemset;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 题单题目项 VO
 */
@Data
public class ProblemSetProblemItemVO {
    /**
     * 题目 pid
     */
    private String pid;

    /**
     * 题目标题
     */
    private String title;

    /**
     * 排序顺序
     */
    @JsonProperty("sort_order")
    private Integer sortOrder;
}
