package com.seuoj.seuojbackend.common;

import lombok.Getter;

import java.util.List;

/**
 * 评测状态枚举类（还没有和评测端沟通具体编码，目前仅为示例实现）
 */
@Getter
public enum SubmissionStatus {
    PENDING("Pending"),
    RUNNING("Running"),
    FAILED("Failed"), // 评测提交失败（评测端异常导致）
    SUCCESS("Success"),
    COMPILE_ERROR("CompileError"),
    JUDGE_ERROR("JudgeError");

    private final String status;

    SubmissionStatus(String status) {
        this.status = status;
    }

    /**
     * 获取可被修改的评测状态
     */
    public static List<String> getModifiableStatusStrs() {
        return List.of(PENDING.getStatus(), RUNNING.getStatus(), FAILED.getStatus());
    }

}
