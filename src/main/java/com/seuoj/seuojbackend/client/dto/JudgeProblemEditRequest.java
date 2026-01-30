package com.seuoj.seuojbackend.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private Info info;
    private List<Example> example;
    private Interactor interactor;
    private Checker checker;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Example {
        private String in;
        private String ans;
        private String description;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
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

        @JsonProperty("test_case_number")
        private Integer testCaseNumber;

        @JsonProperty("problem_type")
        private String problemType;

        @JsonProperty("checker_type")
        private String checkerType;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Interactor {
        private String type;
        private String data;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Checker {
        private String type;
        private String data;
    }
}
