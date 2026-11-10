ALTER TABLE contest_register_rel
    MODIFY COLUMN user_id bigint NULL COMMENT '关联账号，生成前为空',
    ADD COLUMN student_no varchar(100) NOT NULL DEFAULT '',
    ADD COLUMN college varchar(100) NOT NULL DEFAULT '',
    ADD COLUMN class_name varchar(100) NOT NULL DEFAULT '',
    ADD COLUMN contact_email varchar(254) NOT NULL DEFAULT '',
    ADD COLUMN notes varchar(1000) NOT NULL DEFAULT '',
    ADD COLUMN active_student_key varchar(140) GENERATED ALWAYS AS
        (CASE WHEN is_del=0 AND student_no<>'' THEN concat(contest_id,'#',student_no) ELSE NULL END) STORED,
    ADD UNIQUE INDEX uk_contest_student_active (active_student_key);

ALTER TABLE user_info
    ADD COLUMN temporary_contest_id bigint NULL,
    ADD COLUMN is_enabled tinyint(1) NOT NULL DEFAULT 1,
    ADD COLUMN temporary_username varchar(64) GENERATED ALWAYS AS
        (CASE WHEN is_del=0 AND temporary_contest_id IS NOT NULL THEN username ELSE NULL END) STORED,
    ADD UNIQUE INDEX uk_user_temporary_username (temporary_username);
