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

    @JsonProperty("fail_count")
    private int failCount;

    private List<SuccessDetail> successes = new ArrayList<>();
    private List<FailDetail> failures = new ArrayList<>();

    @Data
    public static class SuccessDetail {
        private int row;

        @JsonProperty("student_id")
        private String studentId;

        private String name;
        private String email;
        private String password;

        @JsonProperty("existing_account")
        private boolean existingAccount;

        public SuccessDetail(int row, String studentId, String name, String email, String password,
                boolean existingAccount) {
            this.row = row;
            this.studentId = studentId;
            this.name = name;
            this.email = email;
            this.password = password;
            this.existingAccount = existingAccount;
        }
    }

    @Data
    public static class FailDetail {
        private int row;

        @JsonProperty("student_id")
        private String studentId;

        private String name;
        private String reason;

        public FailDetail(int row, String studentId, String name, String reason) {
            this.row = row;
            this.studentId = studentId;
            this.name = name;
            this.reason = reason;
        }
    }
}
