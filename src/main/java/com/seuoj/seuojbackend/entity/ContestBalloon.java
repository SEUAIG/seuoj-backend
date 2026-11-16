package com.seuoj.seuojbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 比赛气球队列：某选手首次 AC 某题时入队，志愿者送达后标记为 DELIVERED。
 * 去重由 (contest_id, user_id, problem_id) 的生成列唯一索引保证。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("contest_balloon")
public class ContestBalloon implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("contest_id")
    private Long contestId;

    @TableField("submission_id")
    private Long submissionId;

    @TableField("user_id")
    private Long userId;

    @TableField("problem_id")
    private Long problemId;

    @TableField("color")
    private String color;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("delivered_at")
    private LocalDateTime deliveredAt;

    @TableField("delivered_by")
    private Long deliveredBy;

    @TableField("is_del")
    @TableLogic
    private Integer isDel;
}
