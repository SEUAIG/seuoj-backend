package com.seuoj.seuojbackend.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import lombok.Data;

/**
 * 题目配置信息
 */
@Data
public class ProblemConfigDTO {
    @JsonProperty("problem_info")
    private ProblemInfo problemInfo;

    @JsonProperty("testcases")
    private List<Testcase> testcases;

    @JsonProperty("subtasks")
    private List<Subtask> subtasks;

    @JsonProperty("custom_modules")
    private CustomModules customModules;

    @Data
    public static class ProblemInfo {
        @JsonProperty("problem_type")
        private String problemType;

        @JsonProperty("checker_type")
        private String checkerType;

        @JsonProperty("time_limit_ms")
        private Long timeLimitMs;

        @JsonProperty("memory_limit_kb")
        private Long memoryLimitKb;
    }

    @Data
    public static class Testcase {
        private Integer id;

        @JsonProperty("in_path")
        private String inPath;

        @JsonProperty("ans_path")
        private String ansPath;

        private Integer weight;

        @JsonProperty("time_limit_ms")
        private Long timeLimitMs;

        @JsonProperty("memory_limit_kb")
        private Long memoryLimitKb;
    }

    @Data
    public static class Subtask {
        private Integer id;
        private List<Integer> cases;

        @JsonProperty("pre_subtasks")
        private List<Integer> preSubtasks;

        private Integer score;

        private String type;
    }

    @Data
    public static class CustomModules {
        @JsonProperty("checker_path")
        private String checkerPath;

        @JsonProperty("interactor_path")
        private String interactorPath;
    }
}
