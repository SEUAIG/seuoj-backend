-- Apply once to existing databases before deploying the matching backend.
ALTER TABLE contest_register_rel
    ADD COLUMN display_name varchar(100) NOT NULL DEFAULT '' COMMENT '本场比赛榜单姓名',
    ADD COLUMN is_starred tinyint(1) NOT NULL DEFAULT 0 COMMENT '打星，不占正式名次',
    ADD COLUMN is_managed tinyint(1) NOT NULL DEFAULT 0 COMMENT '由比赛管理员维护';
