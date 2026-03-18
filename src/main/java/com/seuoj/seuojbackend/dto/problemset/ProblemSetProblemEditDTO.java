package com.seuoj.seuojbackend.dto.problemset;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 全量覆盖题单题目列表请求参数
 */
@Data
public class ProblemSetProblemEditDTO {

    /**
     * 题目列表
     */
    @NotNull(message = "problem_list 不能为空")
    @JsonProperty("problem_list")
    private List<@Valid ProblemItemDTO> problemList;

    /**
     * 题目项
     */
    @Data
    public static class ProblemItemDTO {
        /**
         * 题目 pid
         */
        @NotBlank(message = "pid 不能为空")
        private String pid;

        /**
         * 题目标题
         */
        private String title;

        /**
         * 排序顺序
         */
        @NotNull(message = "order_id 不能为空")
        @Min(value = 1, message = "order_id 必须为正整数")
        @JsonProperty("order_id")
        private Integer orderId;
    }
}

