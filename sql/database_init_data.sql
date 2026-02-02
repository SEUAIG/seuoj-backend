SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO `problem` (id, pid, title, total_submit, total_accept)
VALUES (1, 'p01', 'a+b', 0, 0);

-- ----

INSERT INTO `tag_group` (id, type, name, created_at, updated_at, is_del) VALUES
(1, 'algorithm', NULL, NOW(), NOW(), 0),
(2, 'algorithm', '基础算法', NOW(), NOW(), 0),
(3, 'algorithm', '高级算法', NOW(), NOW(), 0),
(4, 'source', NULL, NOW(), NOW(), 0),
(5, 'source', '竞赛来源', NOW(), NOW(), 0),
(6, 'time', NULL, NOW(), NOW(), 0),
(7, 'time', '时间复杂度', NOW(), NOW(), 0),
(8, 'special', NULL, NOW(), NOW(), 0),
(9, 'special', '专题训练', NOW(), NOW(), 0);

-- ----

INSERT INTO `tag` (id, tag_name, group_id, created_at, updated_at, is_del)
VALUES
(1, '贪心', 2, NOW(), NOW(), 0),
(2, '动态规划', 2, NOW(), NOW(), 0),
(3, '图论', 3, NOW(), NOW(), 0),
(4, '交互题', 8, NOW(), NOW(), 0),
(5, '冷门标签', 1, NOW(), NOW(), 0);

-- ----

INSERT INTO `problem_tag_rel` (id, problem_id, tag_id)
VALUES (1, 1, 1),
       (2, 1, 2);

-- ----

INSERT INTO `user_info` (id, username, email, password)
VALUES (1, '123', '1234567891@qq.com', '$10$hwsFr7kTG6B4NSBrCg45aOfUDI8q1BgP7Vv88ADR9DCs8WWkFRyuq');


-- ----

INSERT INTO `user_role`
VALUES (1, 'USER', 'USER', 0),
    (2, 'ADMIN', 'ADMIN', 0),
    (3, 'SUPER_ADMIN', 'SUPER_ADMIN', 0);

-- ----

INSERT INTO `user_role_rel`
VALUES (1, 1, 1, 0, DEFAULT),
       (2, 1, 2, 0, DEFAULT),
       (3, 1, 3, 0, DEFAULT);


SET FOREIGN_KEY_CHECKS = 1;
