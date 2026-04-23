package com.seuoj.seuojbackend.dto.assignment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AssignmentImportFromProblemSetDTO {
    @JsonProperty("problem_set_id")
    private Long problemSetId;
}
