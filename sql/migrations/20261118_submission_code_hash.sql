-- Apply once to existing databases before deploying the matching backend.
-- 提交查重：记录去掉空白后的代码 SHA-256，拦截同一用户在同一题、同一语言、同一场比赛（或同一份作业）下重复提交相同代码。
-- 与 database_schema.sql 中的定义一致。旧提交 code_hash 为 NULL，不参与比较。重复执行会因列已存在而报错，不会改数据。

ALTER TABLE `submission`
    ADD COLUMN `code_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '去掉空白后的代码 SHA-256，用于拦截重复提交' AFTER `assignment_id`,
    ADD INDEX `idx_submission_code_hash` (`user_id` ASC, `problem_id` ASC, `code_hash` ASC) USING BTREE;
