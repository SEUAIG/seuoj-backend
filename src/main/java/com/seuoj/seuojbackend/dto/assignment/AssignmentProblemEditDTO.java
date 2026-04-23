package com.seuoj.seuojbackend.dto.assignment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class AssignmentProblemEditDTO {
    private List<ProblemItem> problems;

    @Data
    public static class ProblemItem {
        @JsonProperty("problem_id")
        private Long problemId;
        private Integer weight;
    }
}
