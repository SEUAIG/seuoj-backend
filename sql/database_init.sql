CREATE DATABASE IF NOT EXISTS seuoj
-- 字符集 排序规则等等
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE seuoj;

DROP TABLE IF EXISTS submission;
DROP TABLE IF EXISTS problem_tag_rel;
DROP TABLE IF EXISTS tag;
DROP TABLE IF EXISTS problem;
DROP TABLE IF EXISTS user_role_rel;
DROP TABLE IF EXISTS user_role;
DROP TABLE IF EXISTS user_info;

CREATE TABLE user_info
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    username   VARCHAR(64)  NOT NULL COMMENT '登录名',
    password   VARCHAR(255) NOT NULL COMMENT '加密密码',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_del     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    UNIQUE KEY uk_username_del (username, is_del) -- 确保未删除用户的用户名唯一
) COMMENT ='用户基础表' CHARACTER SET utf8mb4
                        COLLATE utf8mb4_unicode_ci;

CREATE TABLE user_role
(
    id        INT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(32) NOT NULL UNIQUE COMMENT 'ADMIN/USER/STUDENT/TEACHER',
    role_name VARCHAR(64) NOT NULL,
    is_del    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除'
) COMMENT ='用户角色表' CHARACTER SET utf8mb4
                        COLLATE utf8mb4_unicode_ci;

CREATE TABLE user_role_rel
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT     NOT NULL,
    role_id    INT        NOT NULL,
    is_del     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    active_key VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN is_del = 0 THEN CONCAT(user_id, '#', role_id) ELSE NULL END) STORED,
    UNIQUE KEY uk_user_role_rel_active (active_key),
    CONSTRAINT fk_user_role_rel_user FOREIGN KEY (user_id) REFERENCES user_info (id),
    CONSTRAINT fk_user_role_rel_role FOREIGN KEY (role_id) REFERENCES user_role (id),
    KEY idx_user_role_rel_user_id (user_id),
    KEY idx_user_role_rel_role_id (role_id)
) COMMENT ='用户角色关联表' CHARACTER SET utf8mb4
                            COLLATE utf8mb4_unicode_ci;

CREATE TABLE problem
(
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    pid          VARCHAR(32)  NOT NULL UNIQUE COMMENT '题目编号',
    title        VARCHAR(255) NOT NULL COMMENT '题目标题',
    total_submit INT          NOT NULL DEFAULT 0,
    total_accept INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_del       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除'
) COMMENT ='题目表' CHARACTER SET utf8mb4
                    COLLATE utf8mb4_unicode_ci;

CREATE TABLE tag
(
    id         INT PRIMARY KEY AUTO_INCREMENT,
    tag_name   VARCHAR(64) NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_del     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    UNIQUE KEY uk_tag_name_del (tag_name, is_del) -- 确保未删除标签名唯一
) COMMENT ='题目标签表' CHARACTER SET utf8mb4
                        COLLATE utf8mb4_unicode_ci;

CREATE TABLE problem_tag_rel
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    problem_id BIGINT     NOT NULL,
    tag_id     INT        NOT NULL,
    is_del     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    active_key VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN is_del = 0 THEN CONCAT(problem_id, '#', tag_id) ELSE NULL END) STORED,
    UNIQUE KEY uk_problem_tag_rel_active (active_key),
    CONSTRAINT fk_problem_tag_rel_problem FOREIGN KEY (problem_id) REFERENCES problem (id),
    CONSTRAINT fk_problem_tag_rel_tag FOREIGN KEY (tag_id) REFERENCES tag (id),
    KEY idx_problem_tag_rel_problem_id (problem_id),
    KEY idx_problem_tag_rel_tag_id (tag_id)
) COMMENT ='题目标签关联表' CHARACTER SET utf8mb4
                            COLLATE utf8mb4_unicode_ci;

CREATE TABLE submission
(
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    problem_id    BIGINT      NOT NULL,

    language      VARCHAR(32) NOT NULL,

    status        VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/AC/WA/TLE/RE/CE',

    result_detail JSON COMMENT '评测详细信息',

    submit_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finish_time   DATETIME COMMENT '评测完成时间',

    KEY idx_user (user_id),
    KEY idx_problem (problem_id),
    KEY idx_status (status),
    KEY idx_user_time (user_id, submit_time DESC)
) COMMENT ='用户提交与评测结果表' CHARACTER SET utf8mb4
                                  COLLATE utf8mb4_unicode_ci;




