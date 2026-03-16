package com.seuoj.seuojbackend.vo.problemset;

import java.util.List;
import lombok.Data;

/**
 * 题单分页列表 VO
 */
@Data
public class ProblemSetPageVO {
    /**
     * 当前页码
     */
    private Long current;

    /**
     * 每页条数
     */
    private Long size;

    /**
     * 总条数
     */
    private Long total;

    /**
     * 题单列表
     */
    private List<ProblemSetItemVO> records;
}
