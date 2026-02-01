package com.seuoj.seuojbackend.dto.problem;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    private String title;

    /**
     * 标签ID列表，多选做完全匹配
     */
    private List<Long> tagIds;

    /**
     * 支持前端传 tag_ids 参数（snake_case）
     */
    public void setTag_ids(List<Long> tagIds) {
        this.tagIds = tagIds;
    }
}
