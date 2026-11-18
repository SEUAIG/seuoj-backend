package com.seuoj.seuojbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 站内通知：一人一行，popped_at 为空表示还没在右上角弹过 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_notification")
public class UserNotification implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    /** {@link com.seuoj.seuojbackend.common.NotificationType} 的名字 */
    @TableField("type")
    private String type;

    @TableField("contest_id")
    private Long contestId;

    @TableField("ref_id")
    private Long refId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("popped_at")
    private LocalDateTime poppedAt;
}
