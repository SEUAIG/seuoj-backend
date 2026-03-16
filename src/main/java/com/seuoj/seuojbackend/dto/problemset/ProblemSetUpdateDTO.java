package com.seuoj.seuojbackend.dto.problemset;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 更新题单请求参数
 */
@Data
public class ProblemSetUpdateDTO {
    /**
     * 题单标题
     */
    private String title;

    /**
     * 题单描述
     */
    private String description;

    /**
     * 是否公开
     */
    @JsonProperty("is_public")
    private Boolean isPublic;
}
