-- Apply before deploying the permissions version. Existing role grants remain effective.
-- This migration is applied once; it does not change existing teacher/admin roles.
ALTER TABLE user_info ADD COLUMN allow_nickname_change tinyint(1) NOT NULL DEFAULT 1;
UPDATE user_info SET allow_nickname_change=0 WHERE temporary_contest_id IS NOT NULL;
CREATE TABLE user_capability (
    user_id bigint NOT NULL,
    capability varchar(32) NOT NULL,
    PRIMARY KEY(user_id, capability),
    CONSTRAINT fk_user_capability_user FOREIGN KEY(user_id) REFERENCES user_info(id),
    CONSTRAINT chk_user_capability CHECK (capability IN
      ('PROBLEM_CREATE','PROBLEM_MANAGE','CONTEST_CREATE','CONTEST_MANAGE','USER_MANAGE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
