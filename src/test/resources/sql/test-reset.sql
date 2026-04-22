SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE resource_permission;
TRUNCATE TABLE assignment;
TRUNCATE TABLE contest_problem_rel;
TRUNCATE TABLE contest_register_rel;
TRUNCATE TABLE contest_submission;
TRUNCATE TABLE contest;
TRUNCATE TABLE class_contest_rel;
TRUNCATE TABLE class_student_rel;
TRUNCATE TABLE class_info;
TRUNCATE TABLE problem_set_problem_rel;
TRUNCATE TABLE problem_set;
TRUNCATE TABLE problem_tag_rel;
TRUNCATE TABLE submission;
TRUNCATE TABLE problem;
TRUNCATE TABLE tag;
TRUNCATE TABLE tag_group;
TRUNCATE TABLE user_role_rel;
TRUNCATE TABLE user_role;
TRUNCATE TABLE user_info;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO user_role (id, role_code, role_name, is_del)
VALUES (1, 'STUDENT', '学生', 0),
       (2, 'TEACHER', '教师', 0),
       (3, 'ADMIN', '管理员', 0),
       (4, 'SUPER_ADMIN', '超级管理员', 0);

INSERT INTO user_info (id, public_id, username, email, password, is_del)
VALUES (10001, '00000000-0000-0000-0000-000000010001', 'admin_user', 'admin@test.local',
        '$2a$10$JOD0yzajuN.zC5a2mUaw6uq05DHOeDka9VFM4UWj4xodHAtlvE1O.', 0),
       (10002, '00000000-0000-0000-0000-000000010002', 'normal_user', 'user@test.local',
        '$2a$10$JOD0yzajuN.zC5a2mUaw6uq05DHOeDka9VFM4UWj4xodHAtlvE1O.', 0),
       (10003, '00000000-0000-0000-0000-000000010003', 'teacher_user', 'teacher@test.local',
        '$2a$10$JOD0yzajuN.zC5a2mUaw6uq05DHOeDka9VFM4UWj4xodHAtlvE1O.', 0),
       (10004, '00000000-0000-0000-0000-000000010004', 'second_user', 'second@test.local',
        '$2a$10$JOD0yzajuN.zC5a2mUaw6uq05DHOeDka9VFM4UWj4xodHAtlvE1O.', 0);

INSERT INTO user_role_rel (id, user_id, role_id, is_del)
VALUES (1, 10001, 1, 0),
       (2, 10001, 3, 0),
       (3, 10001, 4, 0),
       (4, 10002, 1, 0),
       (5, 10003, 2, 0),
       (6, 10004, 1, 0);

INSERT INTO problem (id, pid, title, total_submit, total_accept, is_public, is_del)
VALUES (20001, 'p-public', 'Public Problem', 0, 0, 1, 0),
       (20002, 'p-private', 'Private Problem', 0, 0, 0, 0);

INSERT INTO contest (id, public_id, title, subtitle, description, start_time, end_time, rule_type, is_public, is_del)
VALUES (30001, '11111111-1111-1111-1111-111111113001', 'Linked Contest', 'it', 'for delete conflict',
        '2026-01-01 10:00:00', '2026-01-01 12:00:00', 'ACM', 1, 0);

INSERT INTO contest_problem_rel (id, contest_id, problem_id, sort_order, is_del)
VALUES (1, 30001, 20001, 1, 0);

INSERT INTO resource_permission (id, resource_type, resource_id, user_id, permission, granted_by, is_del)
VALUES (1, 'CONTEST', 30001, 10001, 'WRITE', 10001, 0);

INSERT INTO problem_set (id, public_id, title, description, created_by_user_id, is_public, is_del)
VALUES (40001, '22222222-2222-2222-2222-222222224001', 'Linked ProblemSet', 'for delete conflict', 10001, 1, 0);

INSERT INTO resource_permission (id, resource_type, resource_id, user_id, permission, granted_by, is_del)
VALUES (2, 'PROBLEM_SET', 40001, 10001, 'WRITE', 10001, 0);

INSERT INTO problem_set_problem_rel (id, problem_set_id, problem_id, sort_order, weight, is_del)
VALUES (1, 40001, 20001, 1, 100, 0);
