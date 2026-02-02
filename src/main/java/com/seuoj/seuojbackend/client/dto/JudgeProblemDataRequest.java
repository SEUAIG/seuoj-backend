package com.seuoj.seuojbackend.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * 评测端题目数据上传请求
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JudgeProblemDataRequest {
    private String pid;
    private List<TestcaseItem> testcase;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TestcaseItem {
        private Integer id;

        @JsonProperty("in")
        private String input;

        @JsonProperty("in_name")
        private String inputName;

        @JsonProperty("ans")
        private String answer;

        @JsonProperty("ans_name")
        private String answerName;
    }
}
