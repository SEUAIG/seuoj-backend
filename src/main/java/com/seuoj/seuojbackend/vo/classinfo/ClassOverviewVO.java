package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ClassOverviewVO {

    @JsonProperty("member_count")
    private Integer memberCount;

    @JsonProperty("total_problems")
    private Integer totalProblems;

    private List<StudentOverviewItem> students;

    private List<AssignmentProgressItem> assignments;

    @Data
    public static class StudentOverviewItem {
        @JsonProperty("user_id")
        private Long userId;

        private String username;

        private String nickname;

        @JsonProperty("ac_count")
        private Integer acCount;

        @JsonProperty("submit_count")
        private Integer submitCount;
    }

    @Data
    public static class AssignmentProgressItem {
        @JsonProperty("assignment_id")
        private Long assignmentId;

        private String title;

        private String status;

        private LocalDateTime deadline;

        @JsonProperty("problem_count")
        private Integer problemCount;

        @JsonProperty("avg_completion_rate")
        private Double avgCompletionRate;
    }
}
