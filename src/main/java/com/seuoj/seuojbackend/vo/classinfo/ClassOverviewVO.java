package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class ClassOverviewVO {

    @JsonProperty("member_count")
    private int memberCount;

    @JsonProperty("total_problems")
    private int totalProblems;

    @JsonProperty("problem_sets")
    private List<ProblemSetProgressItem> problemSets;

    private List<StudentOverviewItem> students;

    @Data
    public static class ProblemSetProgressItem {

        @JsonProperty("problem_set_public_id")
        private String problemSetPublicId;

        private String title;

        @JsonProperty("problem_count")
        private int problemCount;

        @JsonProperty("avg_completion_rate")
        private double avgCompletionRate;
    }

    @Data
    public static class StudentOverviewItem {

        @JsonProperty("user_public_id")
        private String userPublicId;

        private String username;

        @JsonProperty("ac_count")
        private int acCount;
    }
}
