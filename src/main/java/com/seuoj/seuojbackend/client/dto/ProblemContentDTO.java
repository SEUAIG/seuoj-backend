package com.seuoj.seuojbackend.client.dto;

import java.util.List;

import lombok.Data;

@Data
public class ProblemContentDTO {
    private String pid;
    private String description;
    private Long timeLimit;
    private Long memLimit;
    private String type;
    private String input;
    private String output;
    private List<Example> example;

    @Data
    public static class Example {
        private String in;
        private String ans;
        private String description;
    }
}
