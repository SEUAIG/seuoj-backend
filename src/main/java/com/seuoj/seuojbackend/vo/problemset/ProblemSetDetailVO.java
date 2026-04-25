package com.seuoj.seuojbackend.vo.problemset;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * 题单详情 VO
 */
@Data
public class ProblemSetDetailVO {
    /**
     * 题单公开 ID
     */
    @JsonProperty("problem_set_id")
    private String problemSetId;

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
     * 题目列表
     */
    @JsonProperty("problem_list")
    private List<ProblemSetProblemItemVO> problemList;

    /**
     * 当前用户是否有写权限
     */
    @JsonProperty("can_write")
    private Boolean canWrite;
}
