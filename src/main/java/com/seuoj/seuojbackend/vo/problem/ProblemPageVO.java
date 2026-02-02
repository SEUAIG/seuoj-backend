package com.seuoj.seuojbackend.vo.problem;

import lombok.Data;

import java.util.List;

/**
 * 题目分页列表VO
 */
@Data
public class ProblemPageVO {
    /**
     * 当前页码，从1开始
     */
    private Integer current;

    /**
     * 每页条数
     */
    private Integer size;

    /**
     * 总条数
     */
    private Long total;

    /**
     * 记录列表
     */
    private List<ProblemListItemVO> records;
}
