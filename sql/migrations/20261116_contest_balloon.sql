-- Apply once to existing databases before deploying the matching backend.
-- 比赛气球：题目配色列 + 气球队列表。与 database_schema.sql 中的定义一致。
-- 线上库已于 2026-11-16 手工建好，本脚本用于其他环境升级；重复执行会因列/表已存在而报错，不会改数据。

ALTER TABLE contest_problem_rel
    ADD COLUMN balloon_color varchar(16) DEFAULT NULL COMMENT '气球颜色(#RRGGBB)，空则按题目顺序自动分配',
    ALGORITHM = INSTANT;

CREATE TABLE `contest_balloon`
(
    `id`            bigint                                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id`    bigint                                                                        NOT NULL COMMENT '比赛ID',
    `submission_id` bigint                                                                        NOT NULL COMMENT '触发入队的提交ID',
    `user_id`       bigint                                                                        NOT NULL COMMENT '选手用户ID',
    `problem_id`    bigint                                                                        NOT NULL COMMENT '题目ID',
    `color`         varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                  NOT NULL COMMENT '气球颜色(#RRGGBB)',
    `status`        enum ('PENDING', 'DELIVERED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '送达状态',
    `created_at`    timestamp                                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
    `delivered_at`  timestamp                                                                     NULL     DEFAULT NULL COMMENT '送达时间',
    `delivered_by`  bigint                                                                        NULL     DEFAULT NULL COMMENT '送达操作人',
    `is_del`        tinyint(1)                                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key`    varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                        when (`is_del` = 0)
                                                                                                            then concat(`contest_id`, _utf8mb4'#', `user_id`, _utf8mb4'#', `problem_id`)
                                                                                                        else NULL end)) STORED NULL COMMENT '首次AC去重键',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_contest_balloon_first_ac` (`active_key`) USING BTREE,
    KEY `idx_contest_balloon_queue` (`contest_id`, `status`, `created_at`) USING BTREE,
    CONSTRAINT `fk_contest_balloon_contest` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_contest_balloon_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_contest_balloon_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  ROW_FORMAT = DYNAMIC COMMENT ='比赛气球队列';

-- 回滚（先回滚程序）：
-- DROP TABLE contest_balloon;
-- ALTER TABLE contest_problem_rel DROP COLUMN balloon_color;
