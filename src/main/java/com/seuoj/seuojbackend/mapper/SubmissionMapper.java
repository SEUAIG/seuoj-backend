package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.vo.me.HeatmapDayVO;
import com.seuoj.seuojbackend.vo.problem.ProblemStatisticsVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

import java.util.List;


/**
 * 用户提交与评测结果表 Mapper 接口
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

    /**
     * 分页查询非比赛提交（联表避免 N+1）。
     * viewerId 非空时只返回该用户自己的提交，以及他人可公开查看的提交（非作业、公开题、题目不在进行中的比赛里）；
     * 为空时不限制（教师、管理员）。
     */
    IPage<SubmissionListItemVO> selectSubmissionPage(Page<?> page,
                                                     @Param("userId") Long userId,
                                                     @Param("verdict") String verdict,
                                                     @Param("assignmentId") Long assignmentId,
                                                     @Param("pid") String pid,
                                                     @Param("language") String language,
                                                     @Param("username") String username,
                                                     @Param("viewerId") Long viewerId,
                                                     @Param("now") LocalDateTime now);

    /**
     * 同一用户在同一题、同一语言、同一场比赛（或同一份作业，或都在两者之外）下，代码哈希相同的提交数。
     * 评测系统故障（Failed / JudgendError / SystemError）、已删除、被跳过的不算。
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM submission s
            WHERE s.user_id = #{userId} AND s.problem_id = #{problemId} AND s.code_hash = #{codeHash}
              AND s.language = #{language} AND s.is_del = 0 AND s.is_skipped = 0
              AND s.status != 'Failed' AND (s.verdict IS NULL OR s.verdict NOT IN ('JudgendError', 'SystemError'))
              <choose>
                <when test="assignmentId != null">AND s.assignment_id = #{assignmentId}</when>
                <otherwise>AND s.assignment_id IS NULL</otherwise>
              </choose>
              <choose>
                <when test="contestId != null">
                  AND EXISTS (SELECT 1 FROM contest_submission cs
                              WHERE cs.submission_id = s.id AND cs.is_del = 0 AND cs.contest_id = #{contestId})
                </when>
                <otherwise>
                  AND NOT EXISTS (SELECT 1 FROM contest_submission cs WHERE cs.submission_id = s.id AND cs.is_del = 0)
                </otherwise>
              </choose>
            </script>
            """)
    long countSameCode(@Param("userId") Long userId, @Param("problemId") Long problemId,
                       @Param("language") String language, @Param("codeHash") String codeHash,
                       @Param("contestId") Long contestId, @Param("assignmentId") Long assignmentId);

    /** 题目在多少场此刻进行中的比赛里 */
    @Select("""
            SELECT COUNT(*) FROM contest_problem_rel cpr
            JOIN contest c ON c.id = cpr.contest_id AND c.is_del = 0
            WHERE cpr.problem_id = #{problemId} AND cpr.is_del = 0
              AND c.start_time <= #{now} AND c.end_time > #{now}
            """)
    long countRunningContestsUsingProblem(@Param("problemId") Long problemId, @Param("now") LocalDateTime now);

    IPage<SubmissionListItemVO> selectAssignmentSubmissionPage(Page<?> page,
                                                               @Param("problemIds") List<Long> problemIds,
                                                               @Param("classId") Long classId,
                                                               @Param("linkedAssignmentId") Long linkedAssignmentId,
                                                               @Param("userId") Long userId,
                                                               @Param("pid") String pid,
                                                               @Param("verdict") String verdict,
                                                               @Param("language") String language,
                                                               @Param("username") String username);

    List<HeatmapDayVO> selectUserHeatmapDays(@Param("userId") Long userId, @Param("year") Integer year);

    List<ProblemStatisticsVO.ScoreDistributionItem> selectScoreDistribution(@Param("problemId") Long problemId);

    List<ProblemStatisticsVO.SubmissionTrendItem> selectSubmissionTrend(
            @Param("problemId") Long problemId,
            @Param("startDate") String startDate);
}
