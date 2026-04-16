package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class ClassProblemSetMatrixVO {

    @JsonProperty("problem_set_title")
    private String problemSetTitle;

    private List<ProblemColumn> problems;

    private List<StudentRow> students;

    @Data
    public static class ProblemColumn {
        private String pid;
        private String title;

        @JsonProperty("sort_order")
        private int sortOrder;
    }

    @Data
    public static class StudentRow {
        private String username;

        @JsonProperty("user_public_id")
        private String userPublicId;

        private List<String> cells;

        @JsonProperty("ac_count")
        private int acCount;
    }
}
