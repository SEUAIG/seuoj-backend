-- Apply once to existing databases before deploying the matching backend.
-- 全站运行时设置（键值）。目前只有 judge.max_concurrent：判题端同时评测的提交数，
-- 由管理后台修改，后端启动后及每 30 秒同步给判题端；没有这一行时沿用判题端 .env 的默认值。
CREATE TABLE system_setting
(
    setting_key   varchar(64)  NOT NULL COMMENT '设置项',
    setting_value varchar(255) NOT NULL COMMENT '值',
    updated_by    bigint       NULL COMMENT '最后修改者',
    updated_at    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (setting_key)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '全站运行时设置';

-- 回滚（先回滚程序）：DROP TABLE system_setting;
