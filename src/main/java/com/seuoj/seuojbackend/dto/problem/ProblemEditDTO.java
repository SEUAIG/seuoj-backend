package com.seuoj.seuojbackend.dto.problem;

import com.seuoj.seuojbackend.common.ProblemCommon;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    private List<Long> tags;

    private String description;

    private String input;

    private String output;

    @Valid
    @NotEmpty(message = "示例不能为空")
    private List<ProblemCommon.Example> example;

    @Valid
    private ProblemCommon.EditInfo info;

    @Valid
    private ProblemCommon.Interactor interactor;

    @Valid
    private ProblemCommon.Checker checker;
}
