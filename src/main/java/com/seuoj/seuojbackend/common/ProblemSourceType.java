package com.seuoj.seuojbackend.common;

import com.seuoj.seuojbackend.exception.BadRequestException;
import java.util.Locale;

public enum ProblemSourceType {
    DIRECT,
    CONTEST,
    PROBLEM_SET,
    ASSIGNMENT;

    public static ProblemSourceType fromRequest(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DIRECT;
        }
        try {
            return ProblemSourceType.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("source_type 非法");
        }
    }
}
