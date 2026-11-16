package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.dto.contest.ContestBalloonProblemRow;
import com.seuoj.seuojbackend.entity.ContestBalloon;
import com.seuoj.seuojbackend.vo.contest.ContestBalloonItemVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContestBalloonMapper extends BaseMapper<ContestBalloon> {

    /** 队列视图：待送达在前，按入队时间升序。 */
    @Select("""
            SELECT b.id,
                   b.problem_id   AS problemId,
                   b.color,
                   b.status,
                   b.created_at   AS createdAt,
                   b.delivered_at AS deliveredAt,
                   p.pid,
                   p.title,
                   u.username,
                   u.nickname
            FROM contest_balloon b
                     LEFT JOIN problem p ON p.id = b.problem_id
                     LEFT JOIN user_info u ON u.id = b.user_id
            WHERE b.contest_id = #{contestId}
              AND b.is_del = 0
            ORDER BY CASE WHEN b.status = 'PENDING' THEN 0 ELSE 1 END,
                     b.created_at ASC,
                     b.id ASC
            """)
    List<ContestBalloonItemVO> selectQueue(@Param("contestId") Long contestId);

    /** 比赛的题目及其显式配置的气球颜色（按题目顺序）。 */
    @Select("""
            SELECT p.pid,
                   p.title,
                   r.balloon_color AS balloonColor,
                   r.sort_order    AS sortIndex
            FROM contest_problem_rel r
                     JOIN problem p ON p.id = r.problem_id
            WHERE r.contest_id = #{contestId}
              AND r.is_del = 0
            ORDER BY r.sort_order
            """)
    List<ContestBalloonProblemRow> selectProblems(@Param("contestId") Long contestId);
}
