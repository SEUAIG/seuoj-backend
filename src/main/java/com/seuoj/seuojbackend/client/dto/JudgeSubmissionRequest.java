package com.seuoj.seuojbackend.client.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交测评
 */
@Data
@NoArgsConstructor
public class JudgeSubmissionRequest {
    private String submissionId;
    private String pid;
    private String code;
    private String language;
    /**
     * 首个测试点失败后跳过其余测试点。仅 ACM 赛制使用；判题端仍会对有子任务的题目忽略该标志。
     */
    private boolean failFast;

    public JudgeSubmissionRequest(String submissionId, String pid, String code, String language) {
        this(submissionId, pid, code, language, false);
    }

    public JudgeSubmissionRequest(String submissionId, String pid, String code, String language, boolean failFast) {
        this.submissionId = submissionId;
        this.pid = pid;
        this.code = code;
        this.language = language;
        this.failFast = failFast;
    }
}
