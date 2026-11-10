-- Apply once to existing databases before deploying the matching backend.
-- Contest announcements reuse `announcement` with target_type = 'CONTEST' (no schema change).
CREATE TABLE IF NOT EXISTS contest_clarification
(
    id                  bigint     NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    contest_id          bigint     NOT NULL COMMENT '比赛ID',
    problem_id          bigint     NULL COMMENT '关联题目ID，空表示整场比赛',
    asker_user_id       bigint     NOT NULL COMMENT '提问者',
    question            text       NOT NULL COMMENT '问题',
    answer              text       NULL COMMENT '回复，空表示未回复',
    answered_by_user_id bigint     NULL COMMENT '回复者',
    answered_at         datetime   NULL COMMENT '回复时间',
    is_public           tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否对所有人公开',
    created_at          datetime   NOT NULL COMMENT '提问时间',
    is_del              tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    INDEX idx_clarification_contest (contest_id, created_at),
    INDEX idx_clarification_asker (contest_id, asker_user_id),
    CONSTRAINT fk_clarification_contest FOREIGN KEY (contest_id) REFERENCES contest (id),
    CONSTRAINT fk_clarification_problem FOREIGN KEY (problem_id) REFERENCES problem (id),
    CONSTRAINT fk_clarification_asker FOREIGN KEY (asker_user_id) REFERENCES user_info (id),
    CONSTRAINT fk_clarification_answerer FOREIGN KEY (answered_by_user_id) REFERENCES user_info (id)
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '比赛答疑';
