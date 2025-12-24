package com.seuoj.seuojbackend.common;

import lombok.Getter;

import java.util.List;

/**
 * 评测提交状态生命周期枚举类
 */
@Getter
public enum SubmissionStatus {
    PENDING("Pending"),
    RUNNING("Running"),
    FAILED("Failed"), // 评测提交失败（评测端异常导致）
    FINISHED("Finished");

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
