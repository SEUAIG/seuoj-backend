package com.seuoj.seuojbackend.vo.problem;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 题目列表项VO
 */
@Data
public class ProblemListItemVO {
    private String pid;
    private String title;

    @JsonProperty("total_submit")
    private Integer totalSubmit;

    @JsonProperty("total_accept")
    private Integer totalAccept;

    private List<String> tags;
}
