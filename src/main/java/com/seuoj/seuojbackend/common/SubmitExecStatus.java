package com.seuoj.seuojbackend.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 评测运行状态
 */
@Getter
public enum SubmitExecStatus {
    SUCCESS("Success"),
    COMPILE_ERROR("CompileError"),
    JUDGEND_ERROR("JudgendError");

    private final String status;

    SubmitExecStatus(String status) {
        this.status = status;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SubmitExecStatus fromString(String value) {
        for (SubmitExecStatus status : SubmitExecStatus.values()) {
            if (status.status.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的 SubmitExecStatus : " + value);
    }

    @JsonValue
    public String getJsonValue() {
        return status;
    }
}
