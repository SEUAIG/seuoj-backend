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
    `is_public`          tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否公开：0-否，1-是',
    `created_by_user_id` bigint                                                        NULL COMMENT '创建者用户ID（仅信息记录，权限走resource_permission）',
    `created_at`         timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`             tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_class_creator` (`created_by_user_id` ASC) USING BTREE,
    CONSTRAINT `fk_class_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
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
-- Table structure for assignment
-- ----------------------------
DROP TABLE IF EXISTS `assignment`;
CREATE TABLE `assignment`
(
    `id`                 bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id`           bigint                                                        NOT NULL COMMENT '班级ID',
    `title`              varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作业标题',
    `description`        text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         NULL COMMENT '作业描述',
    `introduction`       text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         NULL COMMENT '作业介绍（Markdown 富文本）',
    `status`             varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/CLOSED',
    `deadline`           datetime                                                      NULL     COMMENT '截止时间，NULL表示无截止时间',
    `visible_from`       datetime                                                      NULL     COMMENT '可见开始时间，NULL=手动控制',
    `visible_to`         datetime                                                      NULL     COMMENT '可见结束时间，NULL=永久可见',
    `created_by_user_id` bigint                                                        NULL COMMENT '创建者用户ID',
    `created_at`         timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`             tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_assignment_class` (`class_id` ASC) USING BTREE,
    CONSTRAINT `fk_assignment_class` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_assignment_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '作业表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for assignment_problem_rel
-- ----------------------------
DROP TABLE IF EXISTS `assignment_problem_rel`;
CREATE TABLE `assignment_problem_rel`
(
    `id`              bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `assignment_id`   bigint                                                                                                         NOT NULL COMMENT '作业ID',
    `problem_id`      bigint                                                                                                         NOT NULL COMMENT '题目ID',
    `sort_order`      int                                                                                                            NOT NULL COMMENT '排序序号',
    `weight`          int                                                                                                            NOT NULL DEFAULT 1 COMMENT '题目权重，默认1',
    `is_del`          tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key`      varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                              when (`is_del` = 0)
                                                                                                                  then concat(`assignment_id`, _utf8mb4'#', `problem_id`)
                                                                                                              else NULL end)) STORED NULL,
    `active_sort_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                              when (`is_del` = 0)
                                                                                                                  then concat(`assignment_id`, _utf8mb4'#', `sort_order`)
                                                                                                              else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_asn_problem_active` (`active_key` ASC) USING BTREE,
    UNIQUE INDEX `uk_asn_sort_active` (`active_sort_key` ASC) USING BTREE,
    INDEX `idx_asn_problem_rel_asn` (`assignment_id` ASC) USING BTREE,
    INDEX `idx_asn_problem_rel_problem` (`problem_id` ASC) USING BTREE,
    CONSTRAINT `fk_asn_problem_rel_asn` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_asn_problem_rel_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '作业题目关联表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for announcement
-- ----------------------------
DROP TABLE IF EXISTS `announcement`;
CREATE TABLE `announcement`
(
    `id`                 bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `target_type`        varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '目标类型：CLASS/ASSIGNMENT',
    `target_id`          bigint                                                        NOT NULL COMMENT '目标内部ID',
    `title`              varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公告标题',
    `content`            text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci         NULL COMMENT 'Markdown正文',
    `is_pinned`          tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否，1-是',
    `created_by_user_id` bigint                                                        NOT NULL COMMENT '创建者用户ID',
    `created_at`         timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`             tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_announcement_target` (`target_type` ASC, `target_id` ASC) USING BTREE,
    CONSTRAINT `fk_announcement_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '公告表（班级/作业公告与资源）'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for announcement_attachment
-- ----------------------------
DROP TABLE IF EXISTS `announcement_attachment`;
CREATE TABLE `announcement_attachment`
(
    `id`              bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `announcement_id` bigint                                                        NOT NULL COMMENT '公告ID',
    `file_path`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件存储相对路径',
    `file_name`       varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
    `file_size`       bigint                                                        NOT NULL COMMENT '文件大小（字节）',
    `created_at`      timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_del`          tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_attachment_announcement` (`announcement_id` ASC) USING BTREE,
    CONSTRAINT `fk_attachment_announcement` FOREIGN KEY (`announcement_id`) REFERENCES `announcement` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '公告附件表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for class_intro_attachment
-- ----------------------------
DROP TABLE IF EXISTS `class_intro_attachment`;
CREATE TABLE `class_intro_attachment`
(
    `id`              bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id`        bigint                                                        NOT NULL COMMENT '班级ID',
    `file_path`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件存储相对路径',
    `file_name`       varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
    `file_size`       bigint                                                        NOT NULL COMMENT '文件大小（字节）',
    `created_at`      timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_del`          tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_intro_attachment_class` (`class_id` ASC) USING BTREE,
    CONSTRAINT `fk_intro_attachment_class` FOREIGN KEY (`class_id`) REFERENCES `class_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '班级介绍附件表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for assignment_intro_attachment
-- ----------------------------
DROP TABLE IF EXISTS `assignment_intro_attachment`;
CREATE TABLE `assignment_intro_attachment`
(
    `id`              bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `assignment_id`   bigint                                                        NOT NULL COMMENT '作业ID',
    `file_path`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件存储相对路径',
    `file_name`       varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
    `file_size`       bigint                                                        NOT NULL COMMENT '文件大小（字节）',
    `created_at`      timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_del`          tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_asn_intro_att_asn` (`assignment_id` ASC) USING BTREE,
    CONSTRAINT `fk_asn_intro_att_asn` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '作业介绍附件表'
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
    `rule_type`   enum ('NOI','IOI','ACM','CUSTOM') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '赛制类型',
    `is_public`          tinyint(1)                                                                NOT NULL DEFAULT 0 COMMENT '是否公开：0-否，1-是',
    `hide_statistics`    tinyint(1)                                                                NOT NULL DEFAULT 0 COMMENT '比赛期间是否隐藏实时统计：0-否，1-是',
    `scoring_config`     json                                                                      NULL COMMENT '赛制参数覆盖(JSON)',
    `scoring_script`     text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                     NULL COMMENT '自定义赛制脚本(JavaScript), 仅rule_type=CUSTOM时使用',
    `created_by_user_id` bigint                                                                    NULL COMMENT '创建者用户ID（仅信息记录，权限走resource_permission）',
    `created_at`         timestamp                                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         timestamp                                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`             tinyint(1)                                                                NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    CONSTRAINT `fk_contest_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '比赛表'
  ROW_FORMAT = DYNAMIC;

-- contest_manager_rel: REMOVED — replaced by resource_permission(CONTEST, contest_id, user_id, WRITE)

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
    `is_public`          tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否公开，0-不公开，1-公开',
    `created_by_user_id` bigint                                                        NULL COMMENT '创建者用户ID（仅信息记录，权限走resource_permission）',
    `created_at`         timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_del`             tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `pid` (`pid` ASC) USING BTREE,
    FULLTEXT INDEX `idx_problem_title_ft` (`title`) WITH PARSER `ngram`,
    CONSTRAINT `fk_problem_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
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
    `created_by_user_id` bigint                                                        NOT NULL COMMENT '创建者用户ID（仅信息记录，权限走resource_permission）',
    `is_public`          tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否公开：0-否，1-是',
    `created_at`         timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`             tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_problem_set_creator` (`created_by_user_id` ASC) USING BTREE,
    CONSTRAINT `fk_problem_set_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '题单表'
  ROW_FORMAT = DYNAMIC;

-- user_problem_set_rel: REMOVED — replaced by resource_permission(PROBLEM_SET, ps_id, user_id, READ)

-- problem_set_invited_member_rel: REMOVED — replaced by resource_permission(PROBLEM_SET, ps_id, user_id, READ/WRITE)

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
-- Table structure for resource_permission
-- ----------------------------
DROP TABLE IF EXISTS `resource_permission`;
CREATE TABLE `resource_permission`
(
    `id`            bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `resource_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '资源类型：PROBLEM/PROBLEM_SET/CLASS/CONTEST',
    `resource_id`   bigint                                                        NOT NULL COMMENT '资源内部ID',
    `user_id`       bigint                                                        NOT NULL COMMENT '被授权用户ID',
    `permission`    varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '权限类型：READ/WRITE（WRITE蕴含READ）',
    `granted_by`    bigint                                                        NULL COMMENT '授权者用户ID，NULL表示系统自动授权',
    `created_at`    timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_del`        tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `active_key`    varchar(192) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                            when (`is_del` = 0)
                                                                                                                then concat(`resource_type`, _utf8mb4'#', `resource_id`, _utf8mb4'#', `user_id`, _utf8mb4'#', `permission`)
                                                                                                            else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_resource_perm_active` (`active_key` ASC) USING BTREE,
    INDEX `idx_resource_perm_user` (`user_id` ASC, `resource_type` ASC) USING BTREE,
    INDEX `idx_resource_perm_resource` (`resource_type` ASC, `resource_id` ASC) USING BTREE,
    CONSTRAINT `fk_resource_perm_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_resource_perm_granter` FOREIGN KEY (`granted_by`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '统一资源权限表（替代多张专用关联表）'
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
    `assignment_id` bigint                                                       NULL     COMMENT '关联作业ID',
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
    INDEX `idx_assignment` (`assignment_id` ASC) USING BTREE,
    CONSTRAINT `fk_submission_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_submission_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_submission_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
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
    `nickname`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '昵称/显示名',
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

-- ----------------------------
-- Table structure for image_asset
-- ----------------------------
DROP TABLE IF EXISTS `image_asset`;
CREATE TABLE `image_asset`
(
    `id`               bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `image_key`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片访问 key',
    `storage_path`     varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片相对存储路径',
    `mime_type`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '图片 MIME',
    `file_size`        bigint                                                        NOT NULL COMMENT '文件大小（字节）',
    `sha256`           char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci    NOT NULL COMMENT '文件 SHA-256',
    `uploader_user_id` bigint                                                        NOT NULL COMMENT '上传者用户ID',
    `status`           enum ('ACTIVE','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`       timestamp                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted_at`       datetime                                                      NULL COMMENT '删除时间',
    `is_del`           tinyint(1)                                                    NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_image_asset_key` (`image_key` ASC) USING BTREE,
    INDEX `idx_image_asset_sha` (`sha256` ASC) USING BTREE,
    INDEX `idx_image_asset_uploader` (`uploader_user_id` ASC) USING BTREE,
    CONSTRAINT `fk_image_asset_uploader` FOREIGN KEY (`uploader_user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '图片资源表'
  ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for image_binding
-- ----------------------------
DROP TABLE IF EXISTS `image_binding`;
CREATE TABLE `image_binding`
(
    `id`            bigint                                                                                                         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `image_id`      bigint                                                                                                         NOT NULL COMMENT '图片ID',
    `resource_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci                                                   NOT NULL COMMENT '资源类型',
    `resource_id`   bigint                                                                                                         NOT NULL COMMENT '资源ID',
    `created_at`    timestamp                                                                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    timestamp                                                                                                      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_del`        tinyint(1)                                                                                                     NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `active_key`    varchar(192) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case
                                                                                                            when (`is_del` = 0)
                                                                                                                then concat(`image_id`, _utf8mb4'#', `resource_type`, _utf8mb4'#', `resource_id`)
                                                                                                            else NULL end)) STORED NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_image_binding_active` (`active_key` ASC) USING BTREE,
    INDEX `idx_image_binding_resource` (`resource_type` ASC, `resource_id` ASC) USING BTREE,
    INDEX `idx_image_binding_image` (`image_id` ASC) USING BTREE,
    CONSTRAINT `fk_image_binding_image` FOREIGN KEY (`image_id`) REFERENCES `image_asset` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '图片与业务资源绑定关系表'
  ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
