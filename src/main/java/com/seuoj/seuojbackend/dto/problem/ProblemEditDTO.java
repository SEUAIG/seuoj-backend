package com.seuoj.seuojbackend.dto.problem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuoj.seuojbackend.model.ProblemCommon;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

import lombok.Data;

/**
 * 题面编辑请求
 */
@Data
public class ProblemEditDTO {
    @NotBlank(message = "题目 pid 不能为空")
    private String pid;

    private String title;

    @JsonProperty("is_public")
    private Boolean isPublic;

    private List<Long> tags;

    private String description;

    private String input;

    private String output;

    @Valid
    private List<ProblemCommon.Example> example;

    private String hint;
}
