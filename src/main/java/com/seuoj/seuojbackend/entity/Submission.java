package com.seuoj.seuojbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户提交与评测结果表实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "submission", autoResultMap = true)
public class Submission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * Auto-increment primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 提交记录uuid（业务id）
     */
    @TableField(value = "submission_no", fill = FieldFill.INSERT)
    private String submissionNo;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 题目ID
     */
    @TableField("problem_id")
    private Long problemId;

    /**
     * 编程语言
     */
    @TableField("language")
    private String language;

    @TableField("assignment_id")
    private Long assignmentId;

    /**
     * 提交流程状态
     */
    @TableField("status")
    private String status;

    /**
     * 最终判定结果
     */
    @TableField("verdict")
    private String verdict;

    /**
     * 分数
     */
    @TableField("score")
    private Integer score;

    /**
     * 提交时间
     */
    @TableField("submit_time")
    private LocalDateTime submitTime;

    /**
     * 评测完成时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;


    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 是否删除：0-未删除，1-已删除
     */
    @TableField("is_del")
    @TableLogic
    private Integer isDel;

}
