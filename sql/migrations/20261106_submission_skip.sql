-- Apply once to existing databases before deploying the matching backend.
-- 提交 skip：保留原 verdict，统计和榜单都排除 is_skipped = 1 的提交。
-- 追加在表尾并要求 INSTANT，避免在大表上拷表；不支持时会直接报错而不是锁表。
ALTER TABLE submission
    ADD COLUMN is_skipped  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否被管理者跳过，跳过后不计入任何统计',
    ADD COLUMN skipped_at  datetime     NULL DEFAULT NULL COMMENT '跳过时间',
    ADD COLUMN skipped_by  bigint       NULL DEFAULT NULL COMMENT '操作者',
    ADD COLUMN skip_reason varchar(200) NULL DEFAULT NULL COMMENT '跳过原因',
    ALGORITHM = INSTANT;

-- 回滚（先回滚程序；回滚前若有被跳过的提交，题目计数会与旧程序口径不一致，需要按需重算）：
-- ALTER TABLE submission DROP COLUMN skip_reason, DROP COLUMN skipped_by, DROP COLUMN skipped_at, DROP COLUMN is_skipped;
