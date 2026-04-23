package com.seuoj.seuojbackend.service;

import org.springframework.stereotype.Component;

@Component
public class ProblemPidGenerator {

    private static final String PID_PREFIX = "P";
    private static final int PID_DIGIT_WIDTH = 4;

    public String generate(Long sequence) {
        if (sequence == null || sequence <= 0) {
            throw new IllegalArgumentException("problem sequence must be positive");
        }
        return String.format("%s%0" + PID_DIGIT_WIDTH + "d", PID_PREFIX, sequence);
    }
}
