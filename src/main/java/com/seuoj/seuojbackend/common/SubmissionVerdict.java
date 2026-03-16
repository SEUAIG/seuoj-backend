package com.seuoj.seuojbackend.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 最终判题结果枚举类
 */
@Getter
public enum SubmissionVerdict {
    ACCEPTED("Accepted"),
    WRONG_ANSWER("WrongAnswer"),
    TIME_LIMIT_EXCEEDED("TimeLimitExceeded"),
    MEMORY_LIMIT_EXCEEDED("MemoryLimitExceeded"),
    RUNTIME_ERROR("RuntimeError"),
    SYSTEM_ERROR("SystemError"),
    PARTIALLY_ACCEPTED("PartiallyAccepted"),
    SKIPPED("Skipped");

    private final String verdict;

    SubmissionVerdict(String verdict) {
        this.verdict = verdict;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SubmissionVerdict fromString(String verdict) {
        for (SubmissionVerdict value : values()) {
            if (value.verdict.equals(verdict)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知 SubmissionVerdict，无法转换: " + verdict);
    }

    @JsonValue
    public String getVerdict() {
        return verdict;
    }
}
