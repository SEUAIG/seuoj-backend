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
 * 讨论发言，树形。顶层发言 parent_id 为空、root_id 为自身。
 * 删除时有回复就只记 deleted_at 留占位，没有回复才逻辑删除。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("comment")
public class Comment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private Long targetId;

    @TableField("parent_id")
    private Long parentId;

    @TableField("root_id")
    private Long rootId;

    @TableField("depth")
    private Integer depth;

    @TableField("author_user_id")
    private Long authorUserId;

    @TableField("content")
    private String content;

    @TableField("score")
    private Integer score;

    @TableField("reply_count")
    private Integer replyCount;

    @TableField("pinned_at")
    private LocalDateTime pinnedAt;

    @TableField("pinned_by")
    private Long pinnedBy;

    @TableField("hidden_at")
    private LocalDateTime hiddenAt;

    @TableField("hidden_by")
    private Long hiddenBy;

    @TableField("hidden_reason")
    private String hiddenReason;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /** 最后一次修改时间，null 表示没改过 */
    @TableField("edited_at")
    private LocalDateTime editedAt;

    /** 修改次数；当前正文是第 revisionCount + 1 版 */
    @TableField("revision_count")
    private Integer revisionCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("is_del")
    @TableLogic
    private Integer isDel;
}
