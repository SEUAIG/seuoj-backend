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
    `id`         bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id`   bigint                                                                                                         NOT NULL COMMENT '班级ID',
    `contest_id` bigint                                                                                                         NOT NULL COMMENT '比赛ID',
    `is_del`     tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                         when (`is_del` = 0)
                                                                                                             then concat(`class_id`, _utf8mb4'#', `contest_id`)
                                                                                                         else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_class_contest_active` (`active_key` ASC) USING BTREE,
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
    `name`            varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班级名称',
    `description`     text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         NULL COMMENT '班级描述',
    `introduction`    text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         NULL COMMENT '班级介绍（Markdown 富文本）',
    `is_public`       tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否公开：0-否，1-是',
    `teacher_user_id` bigint                                                        NULL COMMENT '教师用户ID 仅教师创建班级时填写',
    `created_at`      timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`          tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_class_teacher` (`teacher_user_id` ASC) USING BTREE,
    CONSTRAINT `fk_class_teacher` FOREIGN KEY (`teacher_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '班级表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for class_student_rel
-- ----------------------------
DROP TABLE IF EXISTS `class_student_rel`;
CREATE TABLE `class_student_rel`
(
    `id`         bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id`   bigint                                                                                                         NOT NULL COMMENT '班级ID',
    `user_id`    bigint                                                                                                         NOT NULL COMMENT '用户ID',
    `joined_at`  datetime                                                                                                       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `is_del`     tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                         when (`is_del` = 0)
                                                                                                             then concat(`class_id`, _utf8mb4'#', `user_id`)
                                                                                                         else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_class_user_active` (`active_key` ASC) USING BTREE,
    INDEX `idx_class_member_class` (`class_id` ASC) USING BTREE,
    INDEX `idx_class_member_user` (`user_id` ASC) USING BTREE,
    CONSTRAINT `fk_class_student_class` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_class_student_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
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
    `id`             bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id`       bigint                                                                                                         NOT NULL COMMENT '班级ID',
    `problem_set_id` bigint                                                                                                         NOT NULL COMMENT '题单ID',
    `is_del`         tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key`     varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                             when (`is_del` = 0)
                                                                                                                 then concat(`class_id`, _utf8mb4'#', `problem_set_id`)
                                                                                                             else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_class_ps_active` (`active_key` ASC) USING BTREE,
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
    `id`          bigint                                                                    NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title`       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci             NOT NULL COMMENT '比赛标题',
    `subtitle`    varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci             NULL     DEFAULT NULL COMMENT '比赛副标题',
    `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                     NULL COMMENT '比赛描述',
    `start_time`  datetime                                                                  NOT NULL COMMENT '开始时间',
    `end_time`    datetime                                                                  NOT NULL COMMENT '结束时间',
    `rule_type`   enum ('NOI','IOI','ACM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '赛制类型',
    `is_public`   tinyint(1)                                                                NOT NULL DEFAULT 0 COMMENT '是否公开：0-否，1-是',
    `created_at`  timestamp                                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  timestamp                                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`      tinyint(1)                                                                NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE
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
    `id`         bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id` bigint                                                                                                         NOT NULL COMMENT '比赛ID',
    `user_id`    bigint                                                                                                         NOT NULL COMMENT '用户ID',
    `is_owner`   tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否拥有者：0-否，1-是',
    `is_del`     tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                         when (`is_del` = 0)
                                                                                                             then concat(`contest_id`, _utf8mb4'#', `user_id`)
                                                                                                         else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_contest_manager_active` (`active_key` ASC) USING BTREE,
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
    `id`                 bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id`         bigint                                                                                                         NOT NULL COMMENT '比赛ID',
    `problem_id`         bigint                                                                                                         NOT NULL COMMENT '题目ID',
    `sort_order`         int                                                                                                            NOT NULL COMMENT '排序序号',
    `is_del`             tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_problem_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                                 when (`is_del` = 0)
                                                                                                                     then concat(`contest_id`, _utf8mb4'#', `problem_id`)
                                                                                                                 else NULL end)) STORED NULL,
    `active_sort_key`    varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                                 when (`is_del` = 0)
                                                                                                                     then concat(`contest_id`, _utf8mb4'#', `sort_order`)
                                                                                                                 else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_contest_problem_active` (`active_problem_key` ASC) USING BTREE,
    UNIQUE INDEX `uk_contest_sort_active` (`active_sort_key` ASC) USING BTREE,
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
    `id`         bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id` bigint                                                                                                         NOT NULL COMMENT '比赛ID',
    `user_id`    bigint                                                                                                         NOT NULL COMMENT '用户ID',
    `joined_at`  datetime                                                                                                       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    `is_del`     tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                         when (`is_del` = 0)
                                                                                                             then concat(`contest_id`, _utf8mb4'#', `user_id`)
                                                                                                         else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_contest_user_active` (`active_key` ASC) USING BTREE,
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
    `id`            bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id`    bigint                                                                                                         NOT NULL COMMENT '比赛ID',
    `submission_id` bigint                                                                                                         NOT NULL COMMENT '提交ID',
    `created_at`    timestamp                                                                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_del`        tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key`    varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                            when (`is_del` = 0)
                                                                                                                then concat(`contest_id`, _utf8mb4'#', `submission_id`)
                                                                                                            else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_contest_submission_active` (`active_key` ASC) USING BTREE,
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
    `pid`          varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '题目编号',
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
    `id`            bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title`         varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题单标题',
    `description`   text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         NULL COMMENT '题单描述',
    `owner_user_id` bigint                                                        NOT NULL COMMENT '题单所属用户ID',
    `is_public`     tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否公开：0-否，1-是',
    `created_at`    timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`        tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_problem_set_owner` (`owner_user_id` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '题单表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_problem_set_rel
-- ----------------------------
DROP TABLE IF EXISTS `user_problem_set_rel`;
CREATE TABLE `user_problem_set_rel`
(
    `id`             bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`        bigint                                                                                                         NOT NULL COMMENT '用户ID',
    `problem_set_id` bigint                                                                                                         NOT NULL COMMENT '题单ID',
    `is_del`         tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key`     varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                             when (`is_del` = 0)
                                                                                                                 then concat(`user_id`, _utf8mb4'#', `problem_set_id`)
                                                                                                             else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_user_problem_set_active` (`active_key` ASC) USING BTREE,
    INDEX `idx_user_problem_set_user` (`user_id` ASC) USING BTREE,
    INDEX `idx_user_problem_set_problem_set` (`problem_set_id` ASC) USING BTREE,
    CONSTRAINT `fk_user_problem_set_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_user_problem_set_problem_set` FOREIGN KEY (`problem_set_id`) REFERENCES `problem_set` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户题单关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for problem_set_invited_member_rel
-- ----------------------------
DROP TABLE IF EXISTS `problem_set_invited_member_rel`;
CREATE TABLE `problem_set_invited_member_rel`
(
    `id`                 bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `problem_set_id`     bigint                                                                                                         NOT NULL COMMENT '题单ID',
    `user_id`            bigint                                                                                                         NOT NULL COMMENT '受邀用户ID',
    `invited_by_user_id` bigint                                                                                                         NOT NULL COMMENT '邀请人用户ID',
    `invited_at`         datetime                                                                                                       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '邀请时间',
    `is_del`             tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key`         varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                                 when (`is_del` = 0)
                                                                                                                     then concat(`problem_set_id`, _utf8mb4'#', `user_id`)
                                                                                                                 else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_problem_set_invited_user_active` (`active_key` ASC) USING BTREE,
    INDEX `idx_ps_invited_member_problem_set` (`problem_set_id` ASC) USING BTREE,
    INDEX `idx_ps_invited_member_user` (`user_id` ASC) USING BTREE,
    INDEX `idx_ps_invited_member_inviter` (`invited_by_user_id` ASC) USING BTREE,
    CONSTRAINT `fk_ps_invited_member_problem_set` FOREIGN KEY (`problem_set_id`) REFERENCES `problem_set` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_ps_invited_member_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_ps_invited_member_inviter` FOREIGN KEY (`invited_by_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '题单受邀成员关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for problem_set_problem_rel
-- ----------------------------
DROP TABLE IF EXISTS `problem_set_problem_rel`;
CREATE TABLE `problem_set_problem_rel`
(
    `id`                 bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `problem_set_id`     bigint                                                                                                         NOT NULL COMMENT '题单ID',
    `problem_id`         bigint                                                                                                         NOT NULL COMMENT '题目ID',
    `sort_order`         int                                                                                                            NOT NULL COMMENT '排序序号',
    `is_del`             tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_problem_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                                 when (`is_del` = 0)
                                                                                                                     then concat(`problem_set_id`, _utf8mb4'#', `problem_id`)
                                                                                                                 else NULL end)) STORED NULL,
    `active_sort_key`    varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                                 when (`is_del` = 0)
                                                                                                                     then concat(`problem_set_id`, _utf8mb4'#', `sort_order`)
                                                                                                                 else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_problem_set_problem_active` (`active_problem_key` ASC) USING BTREE,
    UNIQUE INDEX `uk_problem_set_sort_active` (`active_sort_key` ASC) USING BTREE,
    INDEX `idx_ps_problem_set_sort` (`problem_set_id` ASC, `sort_order` ASC) USING BTREE,
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
    `id`         bigint                                                                                                         NOT NULL AUTO_INCREMENT,
    `problem_id` bigint                                                                                                         NOT NULL,
    `tag_id`     bigint                                                                                                         NOT NULL,
    `is_del`     tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    `active_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                         when (`is_del` = 0)
                                                                                                             then concat(`problem_id`, _utf8mb4'#', `tag_id`)
                                                                                                         else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_problem_tag_rel` (`active_key` ASC) USING BTREE,
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
-- Table structure for submission_detail
-- ----------------------------
DROP TABLE IF EXISTS `submission_detail`;
CREATE TABLE `submission_detail`
(
    `submission_id` bigint                                                 NOT NULL COMMENT '提交ID，与submission表1:1关系',
    `result_detail` json                                                   NULL COMMENT '评测详细信息（每个测试点结果）',
    `subtasks`      json                                                   NULL COMMENT '子任务信息',
    `error_detail`  text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '编译/判题错误详情',
    `created_at`    timestamp                                              NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    timestamp                                              NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`submission_id`) USING BTREE,
    CONSTRAINT `fk_submission_detail_submission` FOREIGN KEY (`submission_id`)
        REFERENCES `submission` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '提交评测详情表（大字段，与submission 1:1）'
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
    `username`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '登录名',
    `email`      varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
    `password`   varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加密密码',
    `created_at` timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_del`     tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_email_del` (`email` ASC, `is_del` ASC) USING BTREE
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

SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO `problem` (id, pid, title, total_submit, total_accept, is_public)
VALUES (1, 'P0001', 'a+b', 0, 0, 1),
       (2, 'P0002', '数组求和', 0, 0, 1),
       (3, 'P0003', '最大子数组和', 0, 0, 1),
       (4, 'P0004', '两数之和', 0, 0, 1),
       (5, 'P0005', '二分查找', 0, 0, 1),
       (6, 'P0006', '合并区间', 0, 0, 1),
       (7, 'P0007', '前K大', 0, 0, 1),
       (8, 'P0008', '迷宫最短路', 0, 0, 1),
       (9, 'P0009', '树的深度优先遍历', 0, 0, 1),
       (10, 'P0010', '迪杰斯特拉', 0, 0, 1),
       (11, 'P0011', 'Floyd最短路', 0, 0, 1),
       (12, 'P0012', '最长公共子序列', 0, 0, 1),
       (13, 'P0013', '01背包', 0, 0, 1),
       (14, 'P0014', '线段树', 0, 0, 1),
       (15, 'P0015', '树状数组', 0, 0, 1),
       (16, 'P0016', '并查集', 0, 0, 1),
       (17, 'P0017', '拓扑排序', 0, 0, 1),
       (18, 'P0018', '强连通分量', 0, 0, 1),
       (19, 'P0019', '最短路径', 0, 0, 1),
       (20, 'P0020', '字符串匹配', 0, 0, 1),
       (21, 'P0021', '回文判断', 0, 0, 1);

INSERT INTO `tag_group` (id, type, name, created_at, updated_at, is_del)
VALUES (1, 'algorithm', NULL, NOW(), NOW(), 0),
       (2, 'algorithm', '基础算法', NOW(), NOW(), 0),
       (3, 'algorithm', '高级算法', NOW(), NOW(), 0),
       (4, 'source', 'NOI系列赛事', NOW(), NOW(), 0),
       (5, 'source', '经典套题', NOW(), NOW(), 0),
       (6, 'source', '国际知名赛事', NOW(), NOW(), 0),
       (7, 'source', '大学竞赛', NOW(), NOW(), 0),
       (8, 'time', NULL, NOW(), NOW(), 0),
       (9, 'special', NULL, NOW(), NOW(), 0);

INSERT INTO `tag` (id, tag_name, group_id, created_at, updated_at, is_del)
VALUES (1, '贪心', 2, NOW(), NOW(), 0),
       (2, '动态规划', 2, NOW(), NOW(), 0),
       (3, '图论', 3, NOW(), NOW(), 0),
       (4, '冷门标签', 1, NOW(), NOW(), 0),
       (5, 'NOI', 4, NOW(), NOW(), 0),
       (6, 'NOIP', 4, NOW(), NOW(), 0),
       (7, 'NOI Online', 4, NOW(), NOW(), 0),
       (8, '经典套题一', 5, NOW(), NOW(), 0),
       (9, '经典套题二', 5, NOW(), NOW(), 0),
       (10, '经典套题三', 5, NOW(), NOW(), 0),
       (11, 'ICPC', 6, NOW(), NOW(), 0),
       (12, 'IOI', 6, NOW(), NOW(), 0),
       (13, 'Google Code Jam', 6, NOW(), NOW(), 0),
       (14, '校内赛', 7, NOW(), NOW(), 0),
       (15, '校级选拔赛', 7, NOW(), NOW(), 0),
       (16, '省赛', 7, NOW(), NOW(), 0),
       (17, '2000', 8, NOW(), NOW(), 0),
       (18, '2001', 8, NOW(), NOW(), 0),
       (19, '2002', 8, NOW(), NOW(), 0),
       (20, '2003', 8, NOW(), NOW(), 0),
       (21, '交互题', 9, NOW(), NOW(), 0),
       (22, '提交答案', 9, NOW(), NOW(), 0),
       (23, 'O2优化', 9, NOW(), NOW(), 0);

INSERT INTO `problem_tag_rel` (id, problem_id, tag_id)
VALUES (1, 1, 1),
       (2, 1, 2),
       (3, 2, 1),
       (4, 2, 4),
       (5, 3, 2),
       (6, 3, 4),
       (7, 4, 1),
       (8, 4, 2),
       (9, 5, 1),
       (10, 5, 4),
       (11, 6, 1),
       (12, 6, 3),
       (13, 7, 3),
       (14, 7, 4),
       (15, 8, 3),
       (16, 8, 4),
       (17, 9, 2),
       (18, 9, 4),
       (19, 10, 3),
       (20, 10, 4),
       (21, 11, 3),
       (22, 11, 4),
       (23, 12, 2),
       (24, 12, 4),
       (25, 13, 2),
       (26, 13, 4),
       (27, 14, 3),
       (28, 14, 4),
       (29, 15, 3),
       (30, 15, 4),
       (31, 16, 1),
       (32, 16, 4),
       (33, 17, 3),
       (34, 17, 4),
       (35, 18, 3),
       (36, 18, 4),
       (37, 19, 3),
       (38, 19, 4),
       (39, 20, 1),
       (40, 20, 4),
       (41, 21, 1),
       (42, 21, 4);

INSERT INTO `user_info` (id, username, email, password)
VALUES (1, '123', '1234567891@qq.com', '$2a$10$JOD0yzajuN.zC5a2mUaw6uq05DHOeDka9VFM4UWj4xodHAtlvE1O.'),
       (2, 'test', 'test@test.com', '$2b$12$ieCFljnNZGJRUNhio1N/s.U8/8R35p74FXKIv/s/1G3pXuEMa1KrK'),
       (3, 'testu', 'testu@test.com', '$2a$10$LSfRC3/lPblawhSjqFUbdOq/kh5zAZGJe5Dwlofs/e8ydUlozesyu');

INSERT INTO `user_role` (id, role_code, role_name, is_del)
VALUES (1, 'USER', 'USER', 0),
       (2, 'ADMIN', 'ADMIN', 0),
       (3, 'SUPER_ADMIN', 'SUPER_ADMIN', 0),
       (4, 'TEACHER', 'TEACHER', 0);

-- 用户角色关联：user_id=1(123) → SUPER_ADMIN, user_id=2(test) → ADMIN, user_id=3(testu) → USER
INSERT INTO `user_role_rel` (id, user_id, role_id, is_del)
VALUES (1, 1, 1, 0),
       (2, 1, 2, 0),
       (3, 1, 3, 0),
       (4, 2, 1, 0),
       (5, 2, 2, 0),
       (6, 3, 1, 0),
       (7, 3, 4, 0);

INSERT INTO `contest` (
    `id`, `title`, `subtitle`, `description`, `start_time`, `end_time`, `rule_type`, `is_public`, `is_del`
)
VALUES (
           1,
           '春季训练赛',
           '热身赛',
           '用于集成测试的最小化预置比赛',
           '2026-03-01 09:00:00',
           '2026-03-01 12:00:00',
           'ACM',
           0,
           0
       );

INSERT INTO `class_info` (`id`, `name`, `description`, `is_public`, `teacher_user_id`, `is_del`)
VALUES (1, '班级一', '用于测试的最小化预置班级', 1, 3, 0);

INSERT INTO `problem_set` (`id`, `title`, `description`, `owner_user_id`, `is_public`, `is_del`)
VALUES (1, '基础题单', '用于测试的最小化预置题单', 1, 1, 0);

INSERT INTO `contest_problem_rel` (`id`, `contest_id`, `problem_id`, `sort_order`, `is_del`)
VALUES (1, 1, 1, 1, 0),
       (2, 1, 2, 2, 0);

INSERT INTO `contest_manager_rel` (`id`, `contest_id`, `user_id`, `is_owner`, `is_del`)
VALUES (1, 1, 1, 1, 0);

INSERT INTO `contest_register_rel` (`id`, `contest_id`, `user_id`, `joined_at`, `is_del`)
VALUES (1, 1, 1, NOW(), 0);

INSERT INTO `problem_set_problem_rel` (`id`, `problem_set_id`, `problem_id`, `sort_order`, `is_del`)
VALUES (1, 1, 1, 1, 0),
       (2, 1, 2, 2, 0);

INSERT INTO `class_problem_set_rel` (`id`, `class_id`, `problem_set_id`, `is_del`)
VALUES (1, 1, 1, 0);

INSERT INTO `class_contest_rel` (`id`, `class_id`, `contest_id`, `is_del`)
VALUES (1, 1, 1, 0);

SET FOREIGN_KEY_CHECKS = 1;
