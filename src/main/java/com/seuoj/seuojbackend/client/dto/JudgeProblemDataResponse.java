package com.seuoj.seuojbackend.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * 评测端题目数据元信息响应
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JudgeProblemDataResponse {
    @JsonProperty("test_cases")
    private List<TestcaseMeta> testCases;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TestcaseMeta {
        private Integer id;

        @JsonProperty("in_name")
        private String inputName;

        @JsonProperty("ans_name")
        private String answerName;
    }
}
