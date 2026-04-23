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
@TableName("assignment")
public class Assignment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("class_id")
    private Long classId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("introduction")
    private String introduction;

    @TableField("status")
    private String status;

    @TableField("deadline")
    private LocalDateTime deadline;

    @TableField("visible_from")
    private LocalDateTime visibleFrom;

    @TableField("visible_to")
    private LocalDateTime visibleTo;

    @TableField("created_by_user_id")
    private Long createdByUserId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("is_del")
    @TableLogic
    private Integer isDel;
}
