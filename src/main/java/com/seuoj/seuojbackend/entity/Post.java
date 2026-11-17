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

/** 帖子：比赛官方题解（EDITORIAL）、用户题解（SOLUTION）、博客（BLOG）。正文只存 Markdown 原文。 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("post")
public class Post implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("kind")
    private String kind;

    @TableField("author_user_id")
    private Long authorUserId;

    @TableField("contest_id")
    private Long contestId;

    @TableField("problem_id")
    private Long problemId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("status")
    private String status;

    @TableField("visible_after")
    private LocalDateTime visibleAfter;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("featured")
    private Boolean featured;

    @TableField("allow_comment")
    private Boolean allowComment;

    @TableField("score")
    private Integer score;

    @TableField("comment_count")
    private Integer commentCount;

    @TableField("hidden_at")
    private LocalDateTime hiddenAt;

    @TableField("hidden_by")
    private Long hiddenBy;

    @TableField("hidden_reason")
    private String hiddenReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("is_del")
    @TableLogic
    private Integer isDel;
}
