CREATE DATABASE IF NOT EXISTS seuoj
-- 字符集 排序规则等等
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE seuoj;

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for class_contest_rel
-- ----------------------------
DROP TABLE IF EXISTS `class_contest_rel`;
CREATE TABLE `class_contest_rel`
(
    `id`         bigint     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id`   bigint     NOT NULL COMMENT '班级ID',
    `contest_id` bigint     NOT NULL COMMENT '比赛ID',
    `is_del`     tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_class_contest_active` (`class_id` ASC, `contest_id` ASC, `is_del` ASC) USING BTREE,
    INDEX `idx_class_contest_class` (`class_id` ASC) USING BTREE,
    INDEX `idx_class_contest_contest` (`contest_id` ASC) USING BTREE,
    CONSTRAINT `fk_class_contest_class` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_class_contest_contest` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '班级比赛关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for class_info
-- ----------------------------
DROP TABLE IF EXISTS `class_info`;
CREATE TABLE `class_info`
(
    `id`              bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `public_id`       char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci     NOT NULL COMMENT '班级公开ID（UUID）',
    `name`            varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班级名称',
    `description`     text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         NULL COMMENT '班级描述',
    `creator_user_id` bigint                                                        NOT NULL COMMENT '创建人用户ID',
    `created_at`      timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`          tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_public_id` (`public_id` ASC) USING BTREE,
    INDEX `idx_class_creator` (`creator_user_id` ASC) USING BTREE,
    CONSTRAINT `fk_class_creator` FOREIGN KEY (`creator_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '班级表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for class_member_rel
-- ----------------------------
DROP TABLE IF EXISTS `class_member_rel`;
CREATE TABLE `class_member_rel`
(
    `id`        bigint     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id`  bigint     NOT NULL COMMENT '班级ID',
    `user_id`   bigint     NOT NULL COMMENT '用户ID',
    `joined_at` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `is_del`    tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_class_user_active` (`class_id` ASC, `user_id` ASC, `is_del` ASC) USING BTREE,
    INDEX `idx_class_member_class` (`class_id` ASC) USING BTREE,
    INDEX `idx_class_member_user` (`user_id` ASC) USING BTREE,
    CONSTRAINT `fk_class_member_class` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_class_member_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '班级成员关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for class_problem_set_rel
-- ----------------------------
DROP TABLE IF EXISTS `class_problem_set_rel`;
CREATE TABLE `class_problem_set_rel`
(
    `id`             bigint     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id`       bigint     NOT NULL COMMENT '班级ID',
    `problem_set_id` bigint     NOT NULL COMMENT '题单ID',
    `is_del`         tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_class_ps_active` (`class_id` ASC, `problem_set_id` ASC, `is_del` ASC) USING BTREE,
    INDEX `idx_class_ps_class` (`class_id` ASC) USING BTREE,
    INDEX `idx_class_ps_ps` (`problem_set_id` ASC) USING BTREE,
    CONSTRAINT `fk_class_ps_class` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_class_ps_problem_set` FOREIGN KEY (`problem_set_id`) REFERENCES `problem_set` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '班级题单关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for contest
-- ----------------------------
DROP TABLE IF EXISTS `contest`;
CREATE TABLE `contest`
(
    `id`              bigint                                                                    NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `public_id`       char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                 NOT NULL COMMENT '比赛公开ID（UUID）',
    `title`           varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci             NOT NULL COMMENT '比赛标题',
    `subtitle`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci             NULL     DEFAULT NULL COMMENT '比赛副标题',
    `description`     text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                     NULL COMMENT '比赛描述',
    `start_time`      datetime                                                                  NOT NULL COMMENT '开始时间',
    `end_time`        datetime                                                                  NOT NULL COMMENT '结束时间',
    `rule_type`       enum ('NOI','IOI','ACM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '赛制类型',
    `is_public`       tinyint(1)                                                                NOT NULL DEFAULT 0 COMMENT '是否公开：0-否，1-是',
    `creator_user_id` bigint                                                                    NOT NULL COMMENT '创建人用户ID',
    `created_at`      timestamp                                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      timestamp                                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`          tinyint(1)                                                                NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_public_id` (`public_id` ASC) USING BTREE,
    INDEX `idx_contest_creator` (`creator_user_id` ASC) USING BTREE,
    CONSTRAINT `fk_contest_creator` FOREIGN KEY (`creator_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '比赛表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for contest_manager_rel
-- ----------------------------
DROP TABLE IF EXISTS `contest_manager_rel`;
CREATE TABLE `contest_manager_rel`
(
    `id`         bigint     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id` bigint     NOT NULL COMMENT '比赛ID',
    `user_id`    bigint     NOT NULL COMMENT '用户ID',
    `is_owner`   tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否拥有者：0-否，1-是',
    `is_del`     tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_contest_manager_active` (`contest_id` ASC, `user_id` ASC, `is_del` ASC) USING BTREE,
    INDEX `idx_contest_manager_contest` (`contest_id` ASC) USING BTREE,
    INDEX `idx_contest_manager_user` (`user_id` ASC) USING BTREE,
    CONSTRAINT `fk_contest_manager_contest` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_contest_manager_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '比赛管理者关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for contest_problem_rel
-- ----------------------------
DROP TABLE IF EXISTS `contest_problem_rel`;
CREATE TABLE `contest_problem_rel`
(
    `id`         bigint     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id` bigint     NOT NULL COMMENT '比赛ID',
    `problem_id` bigint     NOT NULL COMMENT '题目ID',
    `sort_order` int        NOT NULL COMMENT '排序序号',
    `is_del`     tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_contest_problem_active` (`contest_id` ASC, `problem_id` ASC, `is_del` ASC) USING BTREE,
    UNIQUE INDEX `uk_contest_sort_active` (`contest_id` ASC, `sort_order` ASC, `is_del` ASC) USING BTREE,
    INDEX `idx_contest_problem_contest` (`contest_id` ASC) USING BTREE,
    INDEX `idx_contest_problem_problem` (`problem_id` ASC) USING BTREE,
    CONSTRAINT `fk_contest_problem_contest` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_contest_problem_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '比赛题目关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for contest_register_rel
-- ----------------------------
DROP TABLE IF EXISTS `contest_register_rel`;
CREATE TABLE `contest_register_rel`
(
    `id`         bigint     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id` bigint     NOT NULL COMMENT '比赛ID',
    `user_id`    bigint     NOT NULL COMMENT '用户ID',
    `joined_at`  datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    `is_del`     tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_contest_user_active` (`contest_id` ASC, `user_id` ASC, `is_del` ASC) USING BTREE,
    INDEX `idx_contest_register_contest` (`contest_id` ASC) USING BTREE,
    INDEX `idx_contest_register_user` (`user_id` ASC) USING BTREE,
    CONSTRAINT `fk_contest_register_contest` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_contest_register_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '比赛报名关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for contest_submission
-- ----------------------------
DROP TABLE IF EXISTS `contest_submission`;
CREATE TABLE `contest_submission`
(
    `id`            bigint     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id`    bigint     NOT NULL COMMENT '比赛ID',
    `submission_id` bigint     NOT NULL COMMENT '提交ID',
    `created_at`    timestamp  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_del`        tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_contest_submission_active` (`contest_id` ASC, `submission_id` ASC, `is_del` ASC) USING BTREE,
    INDEX `idx_contest_submission_contest` (`contest_id` ASC) USING BTREE,
    INDEX `idx_contest_submission_submission` (`submission_id` ASC) USING BTREE,
    CONSTRAINT `fk_contest_submission_contest` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_contest_submission_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '比赛提交关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for problem
-- ----------------------------
DROP TABLE IF EXISTS `problem`;
CREATE TABLE `problem`
(
    `id`           bigint                                                        NOT NULL AUTO_INCREMENT,
    `pid`          varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL DEFAULT '' COMMENT '题目编号',
    `title`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目标题',
    `total_submit` int                                                           NOT NULL DEFAULT 0,
    `total_accept` int                                                           NOT NULL DEFAULT 0,
    `is_public`    tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否公开，0-不公开，1-公开',
    `created_at`   timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_del`       tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `pid` (`pid` ASC) USING BTREE,
    FULLTEXT INDEX `idx_problem_title_ft` (`title`) WITH PARSER `ngram`
) ENGINE = InnoDB
  AUTO_INCREMENT = 2
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '题目表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for problem_set
-- ----------------------------
DROP TABLE IF EXISTS `problem_set`;
CREATE TABLE `problem_set`
(
    `id`              bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `public_id`       char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci     NOT NULL COMMENT '题单公开ID（UUID）',
    `title`           varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题单标题',
    `description`     text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         NULL COMMENT '题单描述',
    `is_public`       tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否公开：0-否，1-是',
    `creator_user_id` bigint                                                        NOT NULL COMMENT '创建人用户ID',
    `created_at`      timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`          tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_public_id` (`public_id` ASC) USING BTREE,
    INDEX `idx_problem_set_creator` (`creator_user_id` ASC) USING BTREE,
    CONSTRAINT `fk_problem_set_creator` FOREIGN KEY (`creator_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '题单表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for problem_set_problem_rel
-- ----------------------------
DROP TABLE IF EXISTS `problem_set_problem_rel`;
CREATE TABLE `problem_set_problem_rel`
(
    `id`             bigint     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `problem_set_id` bigint     NOT NULL COMMENT '题单ID',
    `problem_id`     bigint     NOT NULL COMMENT '题目ID',
    `sort_order`     int        NOT NULL COMMENT '排序序号',
    `is_del`         tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_problem_set_problem_active` (`problem_set_id` ASC, `problem_id` ASC, `is_del` ASC) USING BTREE,
    UNIQUE INDEX `uk_problem_set_sort_active` (`problem_set_id` ASC, `sort_order` ASC, `is_del` ASC) USING BTREE,
    INDEX `idx_ps_problem_set` (`problem_set_id` ASC) USING BTREE,
    INDEX `idx_ps_problem` (`problem_id` ASC) USING BTREE,
    CONSTRAINT `fk_ps_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_ps_problem_set` FOREIGN KEY (`problem_set_id`) REFERENCES `problem_set` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '题单题目关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for problem_tag_rel
-- ----------------------------
DROP TABLE IF EXISTS `problem_tag_rel`;
CREATE TABLE `problem_tag_rel`
(
    `id`         bigint     NOT NULL AUTO_INCREMENT,
    `problem_id` bigint     NOT NULL,
    `tag_id`     bigint     NOT NULL,
    `is_del`     tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_problem_tag_rel` (`problem_id` ASC, `tag_id` ASC) USING BTREE,
    INDEX `idx_problem_tag_rel_problem_id` (`problem_id` ASC) USING BTREE,
    INDEX `idx_problem_tag_rel_tag_id` (`tag_id` ASC) USING BTREE,
    CONSTRAINT `fk_problem_tag_rel_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_problem_tag_rel_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  AUTO_INCREMENT = 3
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '题目标签关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for submission
-- ----------------------------
DROP TABLE IF EXISTS `submission`;
CREATE TABLE `submission`
(
    `id`            bigint                                                       NOT NULL AUTO_INCREMENT,
    `submission_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()) COMMENT 'Business identifier for external reference',
    `user_id`       bigint                                                       NOT NULL,
    `problem_id`    bigint                                                       NOT NULL,
    `language`      varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `status`        varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生命周期状态：Pending/Running/Finished/Failed',
    `verdict`       varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL     DEFAULT NULL COMMENT '最终判定状态：Accepted/WA/TLE/...',
    `result_detail` json                                                         NULL COMMENT '评测详细信息',
    `subtasks`      json                                                         NULL COMMENT '子任务信息',
    `error_detail`  text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci        NULL COMMENT '编译/判题错误详情',
    `score`         int                                                          NULL     DEFAULT NULL COMMENT '得分',
    `submit_time`   datetime                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finish_time`   datetime                                                     NULL     DEFAULT NULL COMMENT '评测完成时间',
    `created_at`    timestamp                                                    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    timestamp                                                    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_del`        tinyint(1)                                                   NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_submission_no` (`submission_no` ASC) USING BTREE,
    INDEX `idx_user` (`user_id` ASC) USING BTREE,
    INDEX `idx_problem` (`problem_id` ASC) USING BTREE,
    INDEX `idx_status` (`status` ASC) USING BTREE,
    INDEX `idx_user_time` (`user_id` ASC, `submit_time` DESC) USING BTREE,
    CONSTRAINT `fk_submission_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_submission_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  AUTO_INCREMENT = 5
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户提交与评测结果表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for tag
-- ----------------------------
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag`
(
    `id`         bigint                                                       NOT NULL AUTO_INCREMENT,
    `tag_name`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `group_id`   bigint                                                       NOT NULL COMMENT '分组ID，关联 tag_group 表',
    `created_at` timestamp                                                    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp                                                    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_del`     tinyint(1)                                                   NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_tag_name_del` (`tag_name` ASC, `is_del` ASC) USING BTREE,
    INDEX `idx_tag_group_id` (`group_id` ASC) USING BTREE,
    CONSTRAINT `fk_tag_group_id` FOREIGN KEY (`group_id`) REFERENCES `tag_group` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '题目标签表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for tag_group
-- ----------------------------
DROP TABLE IF EXISTS `tag_group`;
CREATE TABLE `tag_group`
(
    `id`         bigint                                                       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `type`       varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分组类型，algorithm/source/time/special',
    `name`       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL     DEFAULT NULL COMMENT '分组名称，NULL 表示默认分组',
    `created_at` timestamp                                                    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp                                                    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_del`     tinyint(1)                                                   NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_type_name_del` (`type` ASC, `name` ASC, `is_del` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '标签分组表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_info
-- ----------------------------
DROP TABLE IF EXISTS `user_info`;
CREATE TABLE `user_info`
(
    `id`         bigint                                                        NOT NULL AUTO_INCREMENT,
    `public_id`  char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci     NOT NULL COMMENT '用户公开ID（UUID）',
    `username`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '登录名',
    `email`      varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
    `password`   varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加密密码',
    `created_at` timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_del`     tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_email_del` (`email` ASC, `is_del` ASC) USING BTREE,
    UNIQUE INDEX `uk_public_id` (`public_id` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 2
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户基础表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_role
-- ----------------------------
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role`
(
    `id`        int                                                          NOT NULL AUTO_INCREMENT,
    `role_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ADMIN/USER/STUDENT/TEACHER',
    `role_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `is_del`    tinyint(1)                                                   NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `role_code` (`role_code` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 2
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_role_rel
-- ----------------------------
DROP TABLE IF EXISTS `user_role_rel`;
CREATE TABLE `user_role_rel`
(
    `id`         bigint                                                                                                        NOT NULL AUTO_INCREMENT,
    `user_id`    bigint                                                                                                        NOT NULL,
    `role_id`    int                                                                                                           NOT NULL,
    `is_del`     tinyint(1)                                                                                                    NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    `active_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                        when (`is_del` = 0)
                                                                                                            then concat(`user_id`, _utf8mb4'#', `role_id`)
                                                                                                        else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_user_role_rel_active` (`active_key` ASC) USING BTREE,
    INDEX `idx_user_role_rel_user_id` (`user_id` ASC) USING BTREE,
    INDEX `idx_user_role_rel_role_id` (`role_id` ASC) USING BTREE,
    CONSTRAINT `fk_user_role_rel_role` FOREIGN KEY (`role_id`) REFERENCES `user_role` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_user_role_rel_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  AUTO_INCREMENT = 2
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表'
  ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
