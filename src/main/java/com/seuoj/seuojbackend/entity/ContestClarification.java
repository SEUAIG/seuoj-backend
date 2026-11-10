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

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("contest_clarification")
public class ContestClarification implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("contest_id")
    private Long contestId;

    /** 关联题目；为空表示面向整场比赛 */
    @TableField("problem_id")
    private Long problemId;

    @TableField("asker_user_id")
    private Long askerUserId;

    @TableField("question")
    private String question;

    @TableField("answer")
    private String answer;

    @TableField("answered_by_user_id")
    private Long answeredByUserId;

    @TableField("answered_at")
    private LocalDateTime answeredAt;

    /** 公开后所有能看比赛的人都能看到这一问一答 */
    @TableField("is_public")
    private Boolean isPublic;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("is_del")
    @TableLogic
    private Integer isDel;
}
