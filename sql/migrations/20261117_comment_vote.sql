-- Apply once to existing databases before deploying the matching backend.
-- 题目讨论第 1b-1 期：树形发言 comment + 赞踩 vote。与 database_schema.sql 中的定义一致。
-- 重复执行会因表已存在而报错，不会改数据。

CREATE TABLE `comment`
(
    `id`             bigint                                                                   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `target_type`    enum ('PROBLEM', 'CONTEST', 'POST') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '挂在哪：PROBLEM-题目讨论，CONTEST-整场比赛讨论，POST-帖子评论区',
    `target_id`      bigint                                                                   NOT NULL COMMENT '题目ID / 比赛ID / 帖子ID',
    `parent_id`      bigint                                                                   NULL     DEFAULT NULL COMMENT '回复的发言，NULL 为顶层发言',
    `root_id`        bigint                                                                   NULL     DEFAULT NULL COMMENT '所属顶层发言，顶层发言为自身ID',
    `depth`          int                                                                      NOT NULL DEFAULT 0 COMMENT '层级，顶层为 0',
    `author_user_id` bigint                                                                   NOT NULL COMMENT '作者用户ID',
    `content`        text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                    NOT NULL COMMENT '正文（Markdown 原文，上限 2000 字）',
    `score`          int                                                                      NOT NULL DEFAULT 0 COMMENT '净分（赞-踩），冗余计数',
    `reply_count`    int                                                                      NOT NULL DEFAULT 0 COMMENT '未删除的直接回复数',
    `pinned_at`      datetime                                                                 NULL     DEFAULT NULL COMMENT '置顶时间，非空即置顶',
    `pinned_by`      bigint                                                                   NULL     DEFAULT NULL COMMENT '置顶操作人',
    `hidden_at`      datetime                                                                 NULL     DEFAULT NULL COMMENT '被管理者隐藏的时间',
    `hidden_by`      bigint                                                                   NULL     DEFAULT NULL COMMENT '隐藏操作人',
    `hidden_reason`  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci            NULL     DEFAULT NULL COMMENT '隐藏原因',
    `deleted_at`     datetime                                                                 NULL     DEFAULT NULL COMMENT '有回复时被删除：保留占位，正文不再返回',
    `created_at`     timestamp                                                                NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     timestamp                                                                NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`         tinyint(1)                                                               NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除（没有回复时删除）',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_comment_target` (`target_type`, `target_id`, `parent_id`, `is_del`) USING BTREE,
    KEY `idx_comment_root` (`root_id`) USING BTREE,
    KEY `idx_comment_author` (`author_user_id`, `created_at`) USING BTREE,
    CONSTRAINT `fk_comment_author` FOREIGN KEY (`author_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC COMMENT ='讨论发言（题目讨论、整场比赛讨论、帖子评论区），树形';

CREATE TABLE `vote`
(
    `id`          bigint                                                                NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `target_type` enum ('POST', 'COMMENT') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '投给帖子还是发言',
    `target_id`   bigint                                                                NOT NULL COMMENT '帖子ID / 发言ID',
    `user_id`     bigint                                                                NOT NULL COMMENT '投票人',
    `value`       tinyint                                                               NOT NULL DEFAULT 0 COMMENT '1-赞，-1-踩，0-已取消',
    `created_at`  timestamp                                                             NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  timestamp                                                             NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_vote_target_user` (`target_type`, `target_id`, `user_id`) USING BTREE,
    KEY `idx_vote_user` (`user_id`, `updated_at`) USING BTREE,
    CONSTRAINT `fk_vote_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC COMMENT ='赞踩：每人对每个目标一行';

-- 回滚（先回滚程序）：
-- DROP TABLE vote;
-- DROP TABLE comment;
