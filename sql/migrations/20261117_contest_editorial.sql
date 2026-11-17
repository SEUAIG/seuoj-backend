-- Apply once to existing databases before deploying the matching backend.
-- 比赛官方题解（题目讨论第 1a 期）：比赛开关 + 帖子表 + 帖子附件表。与 database_schema.sql 中的定义一致。
-- 重复执行会因列/表已存在而报错，不会改数据。已有比赛的开关默认关闭。

ALTER TABLE contest
    ADD COLUMN discussion_enabled tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否展示题解与讨论页面：0-否，1-是',
    ALGORITHM = INSTANT;

CREATE TABLE `post`
(
    `id`                   bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `kind`                 enum ('EDITORIAL', 'SOLUTION', 'BLOG') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：EDITORIAL-比赛官方题解，SOLUTION-用户题解，BLOG-博客',
    `author_user_id`       bigint                                                        NOT NULL COMMENT '作者用户ID',
    `contest_id`           bigint                                                        NULL     DEFAULT NULL COMMENT '所属比赛；EDITORIAL 必填，SOLUTION 记发帖来源',
    `problem_id`           bigint                                                        NULL     DEFAULT NULL COMMENT '所属题目；SOLUTION 必填',
    `title`                varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
    `content`              mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   NOT NULL COMMENT '正文（Markdown 原文）',
    `status`               enum ('DRAFT', 'PUBLISHED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    `visible_after`        datetime                                                      NULL     DEFAULT NULL COMMENT 'EDITORIAL 公开时间，NULL 表示比赛结束即公开',
    `published_at`         datetime                                                      NULL     DEFAULT NULL COMMENT '首次发布时间',
    `featured`             tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否精选',
    `allow_comment`        tinyint(1)                                                    NOT NULL DEFAULT 1 COMMENT '是否允许评论',
    `score`                int                                                           NOT NULL DEFAULT 0 COMMENT '净分（赞-踩），冗余计数',
    `comment_count`        int                                                           NOT NULL DEFAULT 0 COMMENT '评论数，冗余计数',
    `hidden_at`            datetime                                                      NULL     DEFAULT NULL COMMENT '被管理者隐藏的时间',
    `hidden_by`            bigint                                                        NULL     DEFAULT NULL COMMENT '隐藏操作人',
    `hidden_reason`        varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL     DEFAULT NULL COMMENT '隐藏原因',
    `created_at`           timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`           timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`               tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_editorial_key` bigint GENERATED ALWAYS AS ((case
                                                            when ((`is_del` = 0) and (`kind` = _utf8mb4'EDITORIAL'))
                                                                then `contest_id`
                                                            else NULL end)) STORED NULL COMMENT '每场比赛最多一篇官方题解',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_post_contest_editorial` (`active_editorial_key`) USING BTREE,
    KEY `idx_post_problem` (`problem_id`, `kind`, `status`, `is_del`) USING BTREE,
    KEY `idx_post_contest` (`contest_id`, `kind`, `status`, `is_del`) USING BTREE,
    KEY `idx_post_author` (`author_user_id`, `kind`, `status`) USING BTREE,
    CONSTRAINT `fk_post_author` FOREIGN KEY (`author_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_post_contest` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_post_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC COMMENT ='帖子：官方题解、用户题解、博客';

CREATE TABLE `post_attachment`
(
    `id`         bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `post_id`    bigint                                                        NOT NULL COMMENT '帖子ID',
    `file_path`  varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件存储相对路径',
    `file_name`  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
    `file_size`  bigint                                                        NOT NULL COMMENT '文件大小（字节）',
    `created_at` timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_del`     tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_post_attachment_post` (`post_id`) USING BTREE,
    CONSTRAINT `fk_post_attachment_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC COMMENT ='帖子附件（官方题解的 PDF）';

-- 回滚（先回滚程序）：
-- DROP TABLE post_attachment;
-- DROP TABLE post;
-- ALTER TABLE contest DROP COLUMN discussion_enabled;
