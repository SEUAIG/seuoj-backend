SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO `problem` (id, pid, title, total_submit, total_accept)
VALUES (1, 'p01', 'a+b', 0, 0);

-- ----

INSERT INTO `problem_tag_rel`
VALUES (1, 1, 1, 0, DEFAULT);
INSERT INTO `problem_tag_rel`
VALUES (2, 1, 2, 0, DEFAULT);

-- ----

-- ----

INSERT INTO `tag` (id, tag_name)
VALUES (1, 'tag1');
INSERT INTO `tag` (id, tag_name)
VALUES (2, 'tag2');
INSERT INTO `tag` (id, tag_name)
VALUES (3, 'tag3');

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
