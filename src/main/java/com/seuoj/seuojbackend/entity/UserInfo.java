package com.seuoj.seuojbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户基础表实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_info")
public class UserInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Auto-increment primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录名
     */
    @TableField("username")
    private String username;

    @TableField("nickname")
    private String nickname;

    @TableField("allow_nickname_change")
    private Boolean allowNicknameChange;

    /**
     * 邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 加密密码
     */
    @TableField("temporary_contest_id")
    private Long temporaryContestId;

    @TableField("is_enabled")
    private Boolean isEnabled;

    @TableField("password")
    private String password;

    /**
     * 封禁时间，空表示未封禁；与比赛账号用的 is_enabled 相互独立
     */
    @TableField("banned_at")
    private LocalDateTime bannedAt;

    /**
     * 封禁截止时间，空表示永久
     */
    @TableField("banned_until")
    private LocalDateTime bannedUntil;

    @TableField("ban_reason")
    private String banReason;

    @TableField("banned_by")
    private Long bannedBy;

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
     * 是否删除，0-未删除，1-已删除
     */
    @TableField("is_del")
    @TableLogic
    private Integer isDel;

}
