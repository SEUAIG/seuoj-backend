package com.seuoj.seuojbackend.mapper;

import com.seuoj.seuojbackend.service.JudgeHostService;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 按评测机查提交（submission.judge_host） */
@Mapper
public interface JudgeHostMapper {
    @Select("SELECT s.submission_no, "
            + "(SELECT MIN(cs.contest_id) FROM contest_submission cs WHERE cs.submission_id = s.id AND cs.is_del = 0) AS contest_id, "
            + "p.pid, p.title AS problem_title, u.username, s.status, s.verdict, s.score, s.judge_started_at, s.judge_ms "
            + "FROM submission s "
            + "LEFT JOIN problem p ON p.id = s.problem_id "
            + "LEFT JOIN user_info u ON u.id = s.user_id "
            + "WHERE s.judge_host = #{host} AND s.is_del = 0 "
            + "ORDER BY s.judge_started_at DESC LIMIT #{limit}")
    @ConstructorArgs({
            @Arg(column = "submission_no", javaType = String.class),
            @Arg(column = "contest_id", javaType = Long.class),
            @Arg(column = "pid", javaType = String.class),
            @Arg(column = "problem_title", javaType = String.class),
            @Arg(column = "username", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "verdict", javaType = String.class),
            @Arg(column = "score", javaType = Integer.class),
            @Arg(column = "judge_started_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "judge_ms", javaType = Integer.class)
    })
    List<JudgeHostService.Judging> recent(@Param("host") String host, @Param("limit") int limit);
}
