package com.seuoj.seuojbackend.dto.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ClassUpdateDTO {

    /**
     * 班级名称
     */
    private String name;

    /**
     * 班级描述
     */
    private String description;

    /**
     * 是否公开班级
     */
    @JsonProperty("is_public")
    private Boolean isPublic;
}