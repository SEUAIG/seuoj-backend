package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ClassItemVO {

    /**
     * 班级公开 ID
     */
    @JsonProperty("class_public_id")
    private String classPublicId;

    /**
     * 班级名称
     */
    private String name;

    /**
     * 班级描述
     */
    private String description;

    /**
     * 创建者公开 ID
     */
    @JsonProperty("creator_public_id")
    private String creatorPublicId;

    /**
     * 是否公开班级
     */
    @JsonProperty("is_public")
    private Boolean isPublic;
}