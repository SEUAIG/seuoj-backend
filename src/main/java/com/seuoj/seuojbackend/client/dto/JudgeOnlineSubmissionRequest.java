package com.seuoj.seuojbackend.client.dto;

import java.util.List;
import lombok.Data;

@Data
public class JudgeOnlineSubmissionRequest {
    private String submissionId;
    private String pid;
    private String code;
    private String language;
    private List<TestcaseItem> testcases;

    @Data
    public static class TestcaseItem {
        private Integer id;
        private String in;
    }
}
