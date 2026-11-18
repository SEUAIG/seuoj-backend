-- Apply once to existing databases before deploying the matching backend.
-- 站内通知：比赛公告、答疑回复、新提问写成一人一行，前端在右上角弹出，弹过就记 popped_at。
-- 与 database_schema.sql 中的定义一致。重复执行会因表已存在而报错，不会改数据。

CREATE TABLE `user_notification`
(
    `id`         bigint                                                       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`    bigint                                                       NOT NULL COMMENT '收件人',
    `type`       varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知类型，见后端 NotificationType',
    `contest_id` bigint                                                       NULL COMMENT '相关比赛',
    `ref_id`     bigint                                                       NULL COMMENT '相关对象：公告ID / 答疑ID',
    `created_at` datetime                                                     NOT NULL COMMENT '事件发生时间',
    `popped_at`  datetime                                                     NULL DEFAULT NULL COMMENT '弹出过的时间，NULL 表示还没弹',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_notification_pending` (`user_id`, `popped_at`, `created_at`) USING BTREE,
    KEY `idx_notification_ref` (`ref_id`, `type`) USING BTREE,
    CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_notification_contest` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC COMMENT ='站内通知（右上角弹窗）';

-- 回滚（先回滚程序）：
-- DROP TABLE user_notification;
