SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO `problem` (id, pid, title, total_submit, total_accept)
VALUES (1, 'p01', 'a+b', 2, 2);

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

INSERT INTO `user_info` (id, username, password)
VALUES (1, '123', '$2a$10$0Sav7AssgISibD2Kd3XTq.wfqMZ4aClgRcZOZqfaEuPn/.dLa4b4y');

-- ----

INSERT INTO `user_role`
VALUES (1, 'STUDENT', 'STUDENT', 0);

-- ----

INSERT INTO `user_role_rel`
VALUES (1, 1, 1, 0, DEFAULT);

SET FOREIGN_KEY_CHECKS = 1;
