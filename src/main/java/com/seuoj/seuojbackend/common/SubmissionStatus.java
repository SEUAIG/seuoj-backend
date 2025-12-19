package com.seuoj.seuojbackend.common;

import lombok.Getter;

/**
 * 评测状态枚举类（还没有和评测端沟通具体编码，目前仅为示例实现）
 */
@Getter
public enum SubmissionStatus {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    FAILED("FAILED"), // 评测提交失败（评测端异常导致）
    AC("AC"),
    WA("WA"),
    TLE("TLE"),
    MLE("MLE"),
    RTE("RTE"),
    CE("CE"),
    SE("SE");

    private final String status;

    SubmissionStatus(String status) {
        this.status = status;
    }

}
