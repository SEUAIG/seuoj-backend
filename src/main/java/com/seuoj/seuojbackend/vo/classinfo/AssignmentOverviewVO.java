package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AssignmentOverviewVO {

    @JsonProperty("assignment_id")
    private Long assignmentId;

    private String title;

    private LocalDateTime deadline;

    @JsonProperty("member_count")
    private Integer memberCount;

    @JsonProperty("problem_count")
    private Integer problemCount;

    @JsonProperty("avg_completion_rate")
    private Double avgCompletionRate;

    private List<ProblemStatItem> problems;

    private List<StudentStatItem> students;

    @Data
    public static class ProblemStatItem {
        private String pid;

        private String title;

        private Integer weight;

        @JsonProperty("ac_count")
        private Integer acCount;

        @JsonProperty("attempt_count")
        private Integer attemptCount;

        @JsonProperty("ac_rate")
        private Double acRate;
    }

    @Data
    public static class StudentStatItem {
        @JsonProperty("user_id")
        private Long userId;

        private String username;

        private String nickname;

        @JsonProperty("ac_count")
        private Integer acCount;

        @JsonProperty("problem_count")
        private Integer problemCount;

        @JsonProperty("submitted_before_deadline")
        private Boolean submittedBeforeDeadline;
    }
}
