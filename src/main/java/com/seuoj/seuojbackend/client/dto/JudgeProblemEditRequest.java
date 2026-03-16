package com.seuoj.seuojbackend.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.seuoj.seuojbackend.common.ProblemCommon;
import java.util.List;
import lombok.Data;

/**
 * 评测端题目编辑请求
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JudgeProblemEditRequest {
    private String pid;
    private String description;
    private String input;
    private String output;
    private List<ProblemCommon.Example> example;
    private String hint;
}
