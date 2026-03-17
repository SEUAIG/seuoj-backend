package com.seuoj.seuojbackend.dto.classinfo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClassCreateDTO {

    /**
     * 班级名称
     */
    @NotBlank(message = "name 不能为空")
    private String name;

    /**
     * 班级描述
     */
    @JsonAlias("discription")
    private String description;

    /**
     * 是否公开班级
     */
    @JsonProperty("is_public")
    private Boolean isPublic;
}