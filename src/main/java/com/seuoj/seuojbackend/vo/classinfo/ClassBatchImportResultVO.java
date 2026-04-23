package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ClassBatchImportResultVO {

    @JsonProperty("total_count")
    private int totalCount;

    @JsonProperty("success_count")
    private int successCount;

    @JsonProperty("skipped_count")
    private int skippedCount;

    @JsonProperty("fail_count")
    private int failCount;

    private List<RowResult> rows = new ArrayList<>();

    @Data
    public static class RowResult {
        private int row;

        @JsonProperty("student_id")
        private String studentId;

        private String name;
        private String email;
        private String password;
        private String status;
        private String detail;

        public RowResult(int row, String studentId, String name, String status) {
            this.row = row;
            this.studentId = studentId;
            this.name = name;
            this.status = status;
        }
    }
}
