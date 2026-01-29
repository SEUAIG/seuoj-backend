package com.seuoj.seuojbackend.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProblemContentDTO {
    private String pid;
    private String description;
    private String input;
    private String output;
    @Valid
    private Info info;
    private Long timeLimit;
    private Long memLimit;
    private String type;
    private List<Example> example;

    @Data
    public static class Example {
        private String in;
        private String ans;
        private String description;
    }

    @Data
    public static class Info {
        @JsonProperty("max_cpu_time_ms")
        private String maxCpuTimeMs = "1000"; // 最大CPU时间限制，单位毫秒(默认1000ms)

        @JsonProperty("max_real_time_ms")
        private String maxRealTimeMs = "2000"; // 最大实际时间限制，单位毫秒(默认2000ms)

        @JsonProperty("max_memory_byte")
        private String maxMemoryByte = "134217728"; // 最大内存限制，单位字节(默认128MB)

        @JsonProperty("max_stack_byte")
        private String maxStackByte = "33554432"; // 最大栈内存限制，单位字节(默认32MB)

        @JsonProperty("max_process_number")
        private String maxProcessNumber = "1"; // 最大进程数(默认1)

        @JsonProperty("max_output_size")
        private String maxOutputSize = "10000"; // 最大输出大小(默认10000)

        @JsonProperty("test_case_number")
        private String testCaseNumber; // 测试点数量

        @JsonProperty("problem_type")
        @NotBlank
        @Pattern(regexp = "^(Standard|Interactive)$")
        private String problemType; // 题目类型："Standard"（标准题）或 "Interactive"（交互式题）\

        @JsonProperty("checker_type")
        @NotBlank
        @Pattern(regexp = "^(Standard|Special)$")
        private String checkerType; // 检查类型："Standard"（标准检查）或 "Special"（特殊检查）
    }
}
