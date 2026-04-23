package com.seuoj.seuojbackend.dto.problem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuoj.seuojbackend.model.ProblemCommon;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

import lombok.Data;

@Data
public class ProblemEditDTO {
    @NotBlank(message = "题目 pid 不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9]{1,19}$", message = "pid 格式无效：须以大写字母开头，仅含大写字母和数字，长度2-20")
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
