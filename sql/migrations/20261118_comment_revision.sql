-- Apply once to existing databases before deploying the matching backend.
-- 讨论发言可以修改并保留历史版本：comment 加两列，新表 comment_revision 存被替换掉的旧正文。
-- 与 database_schema.sql 中的定义一致。重复执行会因列/表已存在而报错，不会改数据。

ALTER TABLE comment
    ADD COLUMN edited_at datetime NULL DEFAULT NULL COMMENT '最后一次修改时间，NULL 表示没改过',
    ADD COLUMN revision_count int NOT NULL DEFAULT 0 COMMENT '修改次数，等于 comment_revision 里的行数',
    ALGORITHM = INSTANT;

CREATE TABLE `comment_revision`
(
    `id`         bigint                                                NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `comment_id` bigint                                                NOT NULL COMMENT '发言ID',
    `version`    int                                                   NOT NULL COMMENT '这份正文是第几版，从 1 开始',
    `content`    text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '被替换掉的旧正文',
    `created_at` timestamp                                             NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '被替换的时间（下一版开始的时间）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_comment_revision_version` (`comment_id`, `version`) USING BTREE,
    CONSTRAINT `fk_comment_revision_comment` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC COMMENT ='讨论发言的历史版本';

-- 回滚（先回滚程序）：
-- DROP TABLE comment_revision;
-- ALTER TABLE comment DROP COLUMN revision_count, DROP COLUMN edited_at;
