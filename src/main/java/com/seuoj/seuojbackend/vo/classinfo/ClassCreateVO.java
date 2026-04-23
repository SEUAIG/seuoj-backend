package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ClassCreateVO {

    /**
     * 班级 ID
     */
    @JsonProperty("class_id")
    private Long classId;
}