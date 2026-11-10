-- Apply once to existing databases before deploying the matching backend.
-- 全站封禁，与比赛临时账号用的 is_enabled 相互独立。
ALTER TABLE user_info
    ADD COLUMN banned_at    datetime     NULL DEFAULT NULL COMMENT '封禁时间，空表示未封禁' AFTER is_enabled,
    ADD COLUMN banned_until datetime     NULL DEFAULT NULL COMMENT '解封时间，空表示永久' AFTER banned_at,
    ADD COLUMN ban_reason   varchar(200) NULL DEFAULT NULL COMMENT '封禁原因' AFTER banned_until,
    ADD COLUMN banned_by    bigint       NULL DEFAULT NULL COMMENT '操作者' AFTER ban_reason;

-- 回滚（先回滚程序再执行）：
-- ALTER TABLE user_info DROP COLUMN banned_by, DROP COLUMN ban_reason, DROP COLUMN banned_until, DROP COLUMN banned_at;
