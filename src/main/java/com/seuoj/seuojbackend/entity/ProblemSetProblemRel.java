package com.seuoj.seuojbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("problem_set_problem_rel")
public class ProblemSetProblemRel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("problem_set_id")
    private Long problemSetId;

    @TableField("problem_id")
    private Long problemId;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("weight")
    private Integer weight;

    @TableField("is_del")
    @TableLogic
    private Integer isDel;
}

