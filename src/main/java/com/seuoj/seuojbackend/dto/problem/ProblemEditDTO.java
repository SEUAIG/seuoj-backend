package com.seuoj.seuojbackend.dto.problem;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private List<Example> example;

    @Valid
    private Info info;

    @Valid
    private Interactor interactor;

    @Valid
    private Checker checker;

    @Data
    public static class Example {
        @NotBlank(message = "示例输入不能为空")
        private String in;
        private String ans;
        private String description;
    }

    @Data
    public static class Info {
        @JsonProperty("max_cpu_time_ms")
        private String maxCpuTimeMs;

        @JsonProperty("max_real_time_ms")
        private String maxRealTimeMs;

        @JsonProperty("max_memory_byte")
        private String maxMemoryByte;

        @JsonProperty("max_stack_byte")
        private String maxStackByte;

        @JsonProperty("max_process_number")
        private String maxProcessNumber;

        @JsonProperty("max_output_size")
        private String maxOutputSize;

        @JsonProperty("problem_type")
        private String problemType;

        @JsonProperty("checker_type")
        private String checkerType;
    }

    @Data
    public static class Interactor {
        @NotBlank(message = "交互器类型不能为空")
        private String type;

        @NotBlank(message = "交互器数据不能为空")
        private String data;
    }

    @Data
    public static class Checker {
        @NotBlank(message = "检查器类型不能为空")
        private String type;

        @NotBlank(message = "检查器数据不能为空")
        private String data;
    }
}
