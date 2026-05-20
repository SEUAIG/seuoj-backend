package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class ContestScriptInputProfileVO {

    @JsonProperty("available_fields")
    private List<ContestScriptInputFieldVO> availableFields;

    @JsonProperty("enabled_fields")
    private List<String> enabledFields;

    @JsonProperty("profile_version")
    private Integer profileVersion;
}
