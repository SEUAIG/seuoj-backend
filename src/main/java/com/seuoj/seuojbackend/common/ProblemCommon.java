package com.seuoj.seuojbackend.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 题目通用结构定义
 */
public final class ProblemCommon {

    private ProblemCommon() {
    }

    @Data
    public static class Example implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @NotBlank(message = "示例输入不能为空")
        private String in;
        private String ans;
        private String description;
    }

    @Data
    public static class EditInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

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
        @Pattern(regexp = "^(Standard|Interactive)$", message = "题目类型必须是 Standard 或 Interactive")
        private String problemType;

        @JsonProperty("checker_type")
        @Pattern(regexp = "^(Standard|Special)$", message = "检查类型必须是 Standard 或 Special")
        private String checkerType;
    }

    @Data
    public static class ContentInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

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
        private String testCaseNumber;

        @JsonProperty("problem_type")
        @Pattern(regexp = "^(Standard|Interactive)$", message = "题目类型必须是 Standard 或 Interactive")
        private String problemType;

        @JsonProperty("checker_type")
        @Pattern(regexp = "^(Standard|Special)$", message = "检查类型必须是 Standard 或 Special")
        private String checkerType;
    }

    @Data
    public static class Interactor implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @NotBlank(message = "交互器类型不能为空")
        @Pattern(regexp = "^(Source|Binary)$", message = "交互器类型必须是 Source 或 Binary")
        private String type;

        @NotBlank(message = "交互器数据不能为空")
        private String data;
    }

    @Data
    public static class Checker implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @NotBlank(message = "检查器类型不能为空")
        @Pattern(regexp = "^(Source|Binary)$", message = "检查器类型必须是 Source 或 Binary")
        private String type;

        @NotBlank(message = "检查器数据不能为空")
        private String data;
    }
}
