package com.seuoj.seuojbackend.vo.contest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ContestScriptInputFieldVO {

    private String key;

    @JsonProperty("label_zh")
    private String labelZh;

    @JsonProperty("description_zh")
    private String descriptionZh;

    @JsonProperty("type_hint")
    private String typeHint;

    @JsonProperty("default_value")
    private Object defaultValue;
}
