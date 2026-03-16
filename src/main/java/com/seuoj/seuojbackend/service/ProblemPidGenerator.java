package com.seuoj.seuojbackend.service;

import org.springframework.stereotype.Component;

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
