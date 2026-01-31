package com.seuoj.seuojbackend.vo.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 题目测试点元信息
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemTestcaseMetaVO {
    private Integer id;

    @JsonProperty("in_name")
    private String inputName;

    @JsonProperty("ans_name")
    private String answerName;
}
