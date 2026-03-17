package com.seuoj.seuojbackend.client.dto;

import java.util.List;

import com.seuoj.seuojbackend.model.ProblemCommon;
import jakarta.validation.Valid;
import lombok.Data;

@Data
public class ProblemContentDTO {
    private String pid;
    private String description;
    private String input;
    private String output;
    @Valid
    private ProblemCommon.ContentInfo info;
    private List<ProblemCommon.Example> example;
    private String hint;
}
