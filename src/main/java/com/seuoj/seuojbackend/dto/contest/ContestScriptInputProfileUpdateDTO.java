package com.seuoj.seuojbackend.dto.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class ContestScriptInputProfileUpdateDTO {

    @JsonProperty("enabled_fields")
    private List<String> enabledFields;
}
