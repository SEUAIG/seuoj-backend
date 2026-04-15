package com.seuoj.seuojbackend.vo.admin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchImportResultVO {

    private int totalCount;
    private int successCount;
    private int failCount;
    private List<FailDetail> failures = new ArrayList<>();

    @Data
    public static class FailDetail {
        private int row;
        private String username;
        private String email;
        private String reason;

        public FailDetail(int row, String username, String email, String reason) {
            this.row = row;
            this.username = username;
            this.email = email;
            this.reason = reason;
        }
    }
}
