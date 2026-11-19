-- Apply once to existing databases before deploying the matching backend.
-- 超级管理员两步验证（TOTP）。totp_enabled=0 且 totp_secret 非空表示绑定到一半。
ALTER TABLE user_info
    ADD COLUMN totp_secret         varchar(64)   NULL DEFAULT NULL COMMENT 'TOTP 密钥（Base32）' AFTER banned_by,
    ADD COLUMN totp_enabled        tinyint(1)    NOT NULL DEFAULT 0 COMMENT '是否已启用两步验证' AFTER totp_secret,
    ADD COLUMN totp_last_step      bigint        NULL DEFAULT NULL COMMENT '最近一次通过的 TOTP 时间步，防重放' AFTER totp_enabled,
    ADD COLUMN totp_recovery_codes varchar(1024) NULL DEFAULT NULL COMMENT '未用恢复码的 SHA-256，逗号分隔' AFTER totp_last_step;

-- 某人丢了验证器和恢复码时，清掉这几列，下次登录会重新要求绑定：
-- UPDATE user_info SET totp_secret = NULL, totp_enabled = 0, totp_last_step = NULL, totp_recovery_codes = NULL WHERE username = '...';

-- 回滚（先回滚程序再执行）：
-- ALTER TABLE user_info DROP COLUMN totp_recovery_codes, DROP COLUMN totp_last_step, DROP COLUMN totp_enabled, DROP COLUMN totp_secret;
