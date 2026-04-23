package com.seuoj.seuojbackend.vo.classinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ClassItemVO {

    /**
     * 班级 ID
     */
    @JsonProperty("class_id")
    private Long classId;

    /**
     * 班级名称
     */
    private String name;

    /**
     * 班级描述
     */
    private String description;

    /**
     * 创建者 ID
     */
    @JsonProperty("creator_id")
    private Long creatorId;

    /**
     * 是否公开班级
     */
    @JsonProperty("is_public")
    private Boolean isPublic;
}