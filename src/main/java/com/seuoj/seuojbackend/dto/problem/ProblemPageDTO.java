package com.seuoj.seuojbackend.dto.problem;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 题目分页查询参数
 */
@Data
public class ProblemPageDTO {
    /**
     * 当前页码，从1开始
     */
    @Min(value = 1, message = "页码最小为1")
    private Integer current = 1;

    /**
     * 每页条数
     */
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private Integer size = 10;

    /**
     * 标题模糊搜索
     */
    @Size(max = 100, message = "标题长度不能超过100")
    private String title;

    /**
     * 标签ID列表，多选做完全匹配
     */
    @JsonProperty("tag_ids")
    @Size(max = 50, message = "标签数量不能超过50")
    private List<Long> tagIds;
}
