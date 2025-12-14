package com.seuoj.seuojbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 题目标签关联表实体类
 *
 * @author YourName
 * @since 2025-12-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("problem_tag_rel")
public class ProblemTagRel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 题目ID
     */
    @TableField("problem_id")
    private String problemId;

    /**
     * 标签ID
     */
    @TableField("tag_id")
    private Integer tagId;

    /**
     * 是否删除，0-未删除，1-已删除
     */
    @TableField("is_del")
    @TableLogic
    private Integer isDel;


}
