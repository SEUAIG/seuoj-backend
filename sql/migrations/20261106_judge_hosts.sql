-- Apply once to existing databases before deploying the matching backend.
-- 记录每份提交最近一次由哪台评测机评测、何时开始、用了多久，供管理后台「评测机」查看和按评测机重测。
-- 加列要求 INSTANT；索引在线建立，不锁表。
ALTER TABLE submission
    ADD COLUMN judge_host       varchar(32) NULL DEFAULT NULL COMMENT '最近一次评测的评测机，如 judge-0',
    ADD COLUMN judge_started_at datetime(3) NULL DEFAULT NULL COMMENT '最近一次评测开始时间',
    ADD COLUMN judge_ms         int         NULL DEFAULT NULL COMMENT '最近一次评测耗时（毫秒，含编译）',
    ALGORITHM = INSTANT;
ALTER TABLE submission
    ADD INDEX idx_submission_judge_host (judge_host, judge_started_at),
    ALGORITHM = INPLACE, LOCK = NONE;

-- 旧设置项 judge.max_concurrent 由 judge.hosts 取代
DELETE FROM system_setting WHERE setting_key = 'judge.max_concurrent';

-- 回滚（先回滚程序）：
-- ALTER TABLE submission DROP INDEX idx_submission_judge_host, DROP COLUMN judge_ms, DROP COLUMN judge_started_at, DROP COLUMN judge_host;
