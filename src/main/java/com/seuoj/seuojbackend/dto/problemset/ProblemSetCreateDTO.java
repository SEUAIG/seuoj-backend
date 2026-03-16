package com.seuoj.seuojbackend.dto.problemset;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建题单请求参数
 */
@Data
public class ProblemSetCreateDTO {
    /**
     * 题单标题
     */
    @NotBlank(message = "title 不能为空")
    private String title;

    /**
     * 题单描述 兼容 discription 字段
     */
    @JsonAlias("discription")
    private String description;

    /**
     * 是否公开
     */
    @NotNull(message = "is_public 不能为空")
    @JsonProperty("is_public")
    private Boolean isPublic;
}
