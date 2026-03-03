SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO `problem` (id, pid, title, total_submit, total_accept, is_public)
VALUES (1, 'p01', 'a+b', 0, 0, 1),
       (2, 'p02', '数组求和', 0, 0, 1),
       (3, 'p03', '最大子数组和', 0, 0, 1),
       (4, 'p04', '两数之和', 0, 0, 1),
       (5, 'p05', '二分查找', 0, 0, 1),
       (6, 'p06', '合并区间', 0, 0, 1),
       (7, 'p07', '前K大', 0, 0, 1),
       (8, 'p08', '迷宫最短路', 0, 0, 1),
       (9, 'p09', '树的深度优先遍历', 0, 0, 1),
       (10, 'p10', '迪杰斯特拉', 0, 0, 1),
       (11, 'p11', 'Floyd最短路', 0, 0, 1),
       (12, 'p12', '最长公共子序列', 0, 0, 1),
       (13, 'p13', '01背包', 0, 0, 1),
       (14, 'p14', '线段树', 0, 0, 1),
       (15, 'p15', '树状数组', 0, 0, 1),
       (16, 'p16', '并查集', 0, 0, 1),
       (17, 'p17', '拓扑排序', 0, 0, 1),
       (18, 'p18', '强连通分量', 0, 0, 1),
       (19, 'p19', '最短路径', 0, 0, 1),
       (20, 'p20', '字符串匹配', 0, 0, 1),
       (21, 'p21', '回文判断', 0, 0, 1);

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

INSERT INTO `user_info` (id, public_id, username, email, password)
VALUES (1, '123', '123', '1234567891@qq.com', '$2a$10$JOD0yzajuN.zC5a2mUaw6uq05DHOeDka9VFM4UWj4xodHAtlvE1O.'),
       (2, '00000000-0000-0000-0000-000000000002', 'test', 'test@test.com', '$2b$12$ieCFljnNZGJRUNhio1N/s.U8/8R35p74FXKIv/s/1G3pXuEMa1KrK'),
       (3, '00000000-0000-0000-0000-000000000003', 'testu', 'testu@test.com', '$2a$10$LSfRC3/lPblawhSjqFUbdOq/kh5zAZGJe5Dwlofs/e8ydUlozesyu');

INSERT INTO `user_role` (id, role_code, role_name, is_del)
VALUES (1, 'USER', 'USER', 0),
       (2, 'ADMIN', 'ADMIN', 0),
       (3, 'SUPER_ADMIN', 'SUPER_ADMIN', 0);

-- 用户角色关联：user_id=1(123) → SUPER_ADMIN, user_id=2(test) → ADMIN, user_id=3(testu) → USER
INSERT INTO `user_role_rel` (id, user_id, role_id, is_del)
VALUES (1, 1, 1, 0),
       (2, 1, 2, 0),
       (3, 1, 3, 0),
       (4, 2, 1, 0),
       (5, 2, 2, 0),
       (6, 3, 1, 0);

INSERT INTO `contest` (
    `id`, `public_id`, `title`, `subtitle`, `description`, `start_time`, `end_time`, `rule_type`, `is_public`, `creator_user_id`, `is_del`
)
VALUES (
    1,
    '11111111-1111-1111-1111-111111111111',
    '春季训练赛',
    '热身赛',
    '用于集成测试的最小化预置比赛',
    '2026-03-01 09:00:00',
    '2026-03-01 12:00:00',
    'ACM',
    0,
    1,
    0
);

INSERT INTO `class_info` (`id`, `public_id`, `name`, `description`, `creator_user_id`, `is_del`)
VALUES (1, '22222222-2222-2222-2222-222222222222', '班级一', '用于测试的最小化预置班级', 1, 0);

INSERT INTO `problem_set` (`id`, `public_id`, `title`, `description`, `is_public`, `creator_user_id`, `is_del`)
VALUES (1, '33333333-3333-3333-3333-333333333333', '基础题单', '用于测试的最小化预置题单', 1, 1, 0);

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

INSERT INTO `class_member_rel` (`id`, `class_id`, `user_id`, `joined_at`, `is_del`)
VALUES (1, 1, 1, NOW(), 0);

INSERT INTO `class_problem_set_rel` (`id`, `class_id`, `problem_set_id`, `is_del`)
VALUES (1, 1, 1, 0);

INSERT INTO `class_contest_rel` (`id`, `class_id`, `contest_id`, `is_del`)
VALUES (1, 1, 1, 0);

SET FOREIGN_KEY_CHECKS = 1;
