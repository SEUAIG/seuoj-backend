package com.seuoj.seuojbackend.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ContestProblemEditDTO {

    @NotNull(message = "problem_list 不能为空")
    @JsonProperty("problem_list")
    private List<@Valid ProblemItemDTO> problemList;

    @Data
    public static class ProblemItemDTO {

        @NotBlank(message = "pid 不能为空")
        private String pid;

        @NotNull(message = "sort_order 不能为空")
        @Min(value = 0, message = "sort_order 不能为负数")
        @JsonProperty("sort_order")
        private Integer sortOrder;
    }
}
