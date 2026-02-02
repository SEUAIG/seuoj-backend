package com.seuoj.seuojbackend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 标签分组实体类
 */
@Data
@TableName("tag_group")
public class TagGroup {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分组类型，algorithm/source/time/special
     */
    @TableField("type")
    private String type;

    /**
     * 分组名称（允许为空，表示默认分组）
     */
    @TableField("name")
    private String name;

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
    @TableLogic
    @TableField(value = "is_del", fill = FieldFill.INSERT)
    private Integer isDel;
}
