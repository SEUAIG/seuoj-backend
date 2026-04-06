package com.seuoj.seuojbackend.dto.problem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.seuoj.seuojbackend.model.ProblemCommon;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ProblemCreateDTO {
    @NotBlank(message = "pid 不能为空")
    private String pid;

    public void setPid(String pid) {
        this.pid = pid == null ? null : pid.trim();
    }

    @NotBlank(message = "title 不能为空")
    private String title;

    @NotNull(message = "is_public 不能为空")
    @JsonProperty("is_public")
    private Boolean isPublic;

    private List<Long> tags;

    private String description;

    private String input;

    private String output;

    @Valid
    @NotNull(message = "example 不能为空")
    private List<ProblemCommon.Example> example;

    private String hint;
}
