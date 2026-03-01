package com.seuoj.seuojbackend.dto.problem;

import com.seuoj.seuojbackend.common.ProblemCommon;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Min(value = 0, message = "isPublic 只能是 0 或 1")
    @Max(value = 1, message = "isPublic 只能是 0 或 1")
    private Integer isPublic;

    private List<Long> tags;

    private String description;

    private String input;

    private String output;

    @Valid
    private List<ProblemCommon.Example> example;

    @Valid
    private ProblemCommon.EditInfo info;

    @Valid
    private ProblemCommon.Interactor interactor;

    @Valid
    private ProblemCommon.Checker checker;
}
