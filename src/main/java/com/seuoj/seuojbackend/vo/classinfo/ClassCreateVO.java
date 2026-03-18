package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ClassCreateVO {

    /**
     * 班级公开 ID
     */
    @JsonProperty("class_public_id")
    private String classPublicId;
}