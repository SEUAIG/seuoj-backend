package com.seuoj.seuojbackend.service;

import org.springframework.stereotype.Component;

/**
 * 题目pid生成器（初步实现待完善）
 */
@Component
public class ProblemPidGenerator {

    private static final String PID_PREFIX = "p";

    public String generate(Long sequence) {
        if (sequence == null || sequence <= 0) {
            throw new IllegalArgumentException("problem sequence must be positive");
        }
        return PID_PREFIX + sequence;
    }
}
