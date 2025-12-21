SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO `problem` (id, pid, title, total_submit, total_accept)
VALUES (1, 'p01', 'a+b', 2, 2);

-- ----

INSERT INTO `problem_tag_rel`
VALUES (1, 1, 1, 0, DEFAULT);
INSERT INTO `problem_tag_rel`
VALUES (2, 1, 2, 0, DEFAULT);

-- ----

INSERT INTO `submission` (id, submission_no, user_id, problem_id, language, status, result_detail, error_detail, submit_time,
                          finish_time)
VALUES (1, 'b1c70a5b-64d1-459d-b355-00d6d7edfa26', 1, 1, 'cpp', 'Success', '{
  \"detail\": \"123\"
}', null, '2025-12-18 17:29:29', '2025-12-18 17:45:39');
INSERT INTO `submission` (id, submission_no, user_id, problem_id, language, status, result_detail, error_detail, submit_time,
                          finish_time)
VALUES (2, '514243a9-8337-45b4-8fad-75766239721d', 1, 1, 'Cpp', 'Success', '{
  \"123\": \"123\"
}', null, '2025-12-19 00:41:32', '2025-12-19 15:59:53');


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
