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
@TableName("submission")
public class Submission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * Auto-increment primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

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

    /**
     * 评测状态
     */
    @TableField("status")
    private String status;

    /**
     * 评测详细信息
     */
    @TableField("result_detail")
    private String resultDetail;

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

}
