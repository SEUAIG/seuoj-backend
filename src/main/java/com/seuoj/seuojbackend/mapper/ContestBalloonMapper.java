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
                   b.submission_id AS submissionId,
                   s.submit_time  AS submitTime,
                   b.color,
                   b.status,
                   b.created_at   AS createdAt,
                   b.delivered_at AS deliveredAt,
                   (s.status = 'Finished'
                       AND (s.verdict <> 'Accepted' OR COALESCE(s.is_skipped, 0) = 1)) AS revoked,
                   p.pid,
                   p.title,
                   u.username,
                   u.nickname
            FROM contest_balloon b
                     LEFT JOIN problem p ON p.id = b.problem_id
                     LEFT JOIN user_info u ON u.id = b.user_id
                     LEFT JOIN submission s ON s.id = b.submission_id
            WHERE b.contest_id = #{contestId}
              AND b.is_del = 0
            ORDER BY CASE WHEN b.status = 'PENDING' THEN 0 ELSE 1 END,
                     b.created_at ASC,
                     b.id ASC
            """)
    List<ContestBalloonItemVO> selectQueue(@Param("contestId") Long contestId);

    /** 比赛的题目及其显式配置的气球颜色（按题目顺序）。 */
    @Select("""
            SELECT r.problem_id    AS problemId,
                   p.pid,
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

    /** 该选手该题在比赛时间内最早的、未被 skip 的 AC 提交；没有则返回 null。 */
    @Select("""
            SELECT s.id
            FROM contest_submission cs
                     JOIN submission s ON s.id = cs.submission_id
                     JOIN contest c ON c.id = cs.contest_id
            WHERE cs.contest_id = #{contestId}
              AND s.user_id = #{userId}
              AND s.problem_id = #{problemId}
              AND s.is_del = 0
              AND s.status = 'Finished'
              AND s.verdict = 'Accepted'
              AND COALESCE(s.is_skipped, 0) = 0
              AND s.submit_time >= c.start_time
              AND s.submit_time <= c.end_time
            ORDER BY s.submit_time, s.id
            LIMIT 1
            """)
    Long selectFirstValidAccepted(@Param("contestId") Long contestId,
                                  @Param("userId") Long userId,
                                  @Param("problemId") Long problemId);
}
