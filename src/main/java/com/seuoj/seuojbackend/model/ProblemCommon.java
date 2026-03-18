package com.seuoj.seuojbackend.model;

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
    public static class ContentInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("max_cpu_time_ms")
        private Long maxCpuTimeMs;

        @JsonProperty("min_cpu_time_ms")
        private Long minCpuTimeMs;

        @JsonProperty("max_memory_kb")
        private Long maxMemoryKb;

        @JsonProperty("min_memory_kb")
        private Long minMemoryKb;

        @JsonProperty("problem_type")
        @Pattern(regexp = "^(Standard|Interactive)$", message = "题目类型必须是 Standard 或 Interactive")
        private String problemType;

        @JsonProperty("checker_type")
        @Pattern(regexp = "^(Standard|Special)$", message = "检查类型必须是 Standard 或 Special")
        private String checkerType;
    }
}
