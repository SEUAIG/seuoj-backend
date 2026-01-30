CREATE DATABASE IF NOT EXISTS seuoj
-- 字符集 排序规则等等
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE seuoj;

-- SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for problem
-- ----------------------------
DROP TABLE IF EXISTS `problem`;
CREATE TABLE `problem`  (
                            `id` bigint NOT NULL AUTO_INCREMENT,
                            `pid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目编号',
                            `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目标题',
                            `total_submit` int NOT NULL DEFAULT 0,
                            `total_accept` int NOT NULL DEFAULT 0,
                            `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            `is_del` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
                            PRIMARY KEY (`id`) USING BTREE,
                            UNIQUE INDEX `pid`(`pid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '题目表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for problem_tag_rel
-- ----------------------------
DROP TABLE IF EXISTS `problem_tag_rel`;
CREATE TABLE `problem_tag_rel`  (
                                    `id` bigint NOT NULL AUTO_INCREMENT,
                                    `problem_id` bigint NOT NULL,
                                    `tag_id` int NOT NULL,
                                    PRIMARY KEY (`id`) USING BTREE,
                                    UNIQUE INDEX `uk_problem_tag_rel`(`problem_id` ASC, `tag_id` ASC) USING BTREE,
                                    INDEX `idx_problem_tag_rel_problem_id`(`problem_id` ASC) USING BTREE,
                                    INDEX `idx_problem_tag_rel_tag_id`(`tag_id` ASC) USING BTREE,
                                    CONSTRAINT `fk_problem_tag_rel_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
                                    CONSTRAINT `fk_problem_tag_rel_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '题目标签关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for submission
-- ----------------------------
DROP TABLE IF EXISTS `submission`;
CREATE TABLE `submission`  (
                               `id` bigint NOT NULL AUTO_INCREMENT,
                               `submission_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT (uuid()) COMMENT 'Business identifier for external reference',
                               `user_id` bigint NOT NULL,
                               `problem_id` bigint NOT NULL,
                               `language` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                               `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生命周期状态：Pending/Running/Finished/Failed',
                               `verdict` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '最终判定状态：Accepted/WA/TLE/...',
                               `result_detail` json NULL COMMENT '评测详细信息',
                               `error_detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '编译/判题错误详情',
                               `submit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               `finish_time` datetime NULL DEFAULT NULL COMMENT '评测完成时间',
                               `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               `is_del` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
                               PRIMARY KEY (`id`) USING BTREE,
                               UNIQUE INDEX `uk_submission_no`(`submission_no` ASC) USING BTREE,
                               INDEX `idx_user`(`user_id` ASC) USING BTREE,
                               INDEX `idx_problem`(`problem_id` ASC) USING BTREE,
                               INDEX `idx_status`(`status` ASC) USING BTREE,
                               INDEX `idx_user_time`(`user_id` ASC, `submit_time` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户提交与评测结果表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for tag
-- ----------------------------
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag`  (
                        `id` int NOT NULL AUTO_INCREMENT,
                        `tag_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                        `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        `is_del` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
                        PRIMARY KEY (`id`) USING BTREE,
                        UNIQUE INDEX `uk_tag_name_del`(`tag_name` ASC, `is_del` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '题目标签表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_info
-- ----------------------------
DROP TABLE IF EXISTS `user_info`;
CREATE TABLE `user_info`  (
                              `id` bigint NOT NULL AUTO_INCREMENT,
                              `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录名',
                              `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加密密码',
                              `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              `is_del` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
                              PRIMARY KEY (`id`) USING BTREE,
                              UNIQUE INDEX `uk_username_del`(`username` ASC, `is_del` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户基础表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_role
-- ----------------------------
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role`  (
                              `id` int NOT NULL AUTO_INCREMENT,
                              `role_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ADMIN/USER/STUDENT/TEACHER',
                              `role_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                              `is_del` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
                              PRIMARY KEY (`id`) USING BTREE,
                              UNIQUE INDEX `role_code`(`role_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_role_rel
-- ----------------------------
DROP TABLE IF EXISTS `user_role_rel`;
CREATE TABLE `user_role_rel`  (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `user_id` bigint NOT NULL,
                                  `role_id` int NOT NULL,
                                  `is_del` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，0-未删除，1-已删除',
                                  `active_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when (`is_del` = 0) then concat(`user_id`,_utf8mb4'#',`role_id`) else NULL end)) STORED NULL,
                                  PRIMARY KEY (`id`) USING BTREE,
                                  UNIQUE INDEX `uk_user_role_rel_active`(`active_key` ASC) USING BTREE,
                                  INDEX `idx_user_role_rel_user_id`(`user_id` ASC) USING BTREE,
                                  INDEX `idx_user_role_rel_role_id`(`role_id` ASC) USING BTREE,
                                  CONSTRAINT `fk_user_role_rel_role` FOREIGN KEY (`role_id`) REFERENCES `user_role` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
                                  CONSTRAINT `fk_user_role_rel_user` FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;




