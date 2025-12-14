package com.seuoj.seuojbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 题目表实体类
 *
 * @author YourName
 * @since 2025-12-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("problem")
public class Problem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * UUID主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 题目编号
     */
    @TableField("pid")
    private String pid;

    /**
     * 题目标题
     */
    @TableField("title")
    private String title;

    /**
     * 总提交数
     */
    @TableField("total_submit")
    private Integer totalSubmit;

    /**
     * 总通过数
     */
    @TableField("total_accept")
    private Integer totalAccept;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 是否删除，0-未删除，1-已删除
     */
    @TableField("is_del")
    @TableLogic
    private Integer isDel;

}