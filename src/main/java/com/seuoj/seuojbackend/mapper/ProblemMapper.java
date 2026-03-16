package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import com.seuoj.seuojbackend.vo.problem.ProblemListItemVO;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 题目表 Mapper 接口
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {
    /**
     * 原子增加题目总提交数
     */
    void atomicallyIncreaseTotalSubmissionCount(@Param("problemId") Long problemId);

    /**
     * 原子增加题目总通过数
     */
    void atomicallyIncreaseTotalAcceptCount(@Param("problemId") Long problemId);

    /**
     * 获取题目详情（不含标签，标签单独查询）
     */
    ProblemDetailVO getProblemDetail(@Param("pid") String pid);

    /**
     * 获取题目标签列表
     */
    List<String> getProblemTags(@Param("pid") String pid);

    /**
     * 分页查询题目列表（支持标题模糊搜索和标签筛选）
     */
    IPage<ProblemListItemVO> selectProblemPage(
            Page<ProblemListItemVO> page,
            @Param("title") String title,
            @Param("titleLike") String titleLike,
            @Param("useFulltext") boolean useFulltext,
            @Param("singleTokens") List<String> singleTokens,
            @Param("useLikeSingle") boolean useLikeSingle,
            @Param("useLikeRaw") boolean useLikeRaw,
            @Param("includeNonPublic") boolean includeNonPublic,
            @Param("tagIds") List<Long> tagIds,
            @Param("tagIdsSize") int tagIdsSize);

    /**
     * 批量获取题目的标签
     */
    List<ProblemTagResult> getProblemTagsBatch(@Param("pids") List<String> pids);

    /**
     * 题目标签关联结果
     */
    @Setter
    @Getter
    class ProblemTagResult {
        private String pid;
        private String tagName;
    }

    /**
     * 统计题目的有效提交数（submission.is_del=0）。
     */
    long countActiveSubmissionsByProblemId(@Param("problemId") Long problemId);

    /**
     * 统计题目在有效比赛中的有效关联数（contest_problem_rel.is_del=0 且 contest.is_del=0）。
     */
    long countActiveContestRelationsByProblemId(@Param("problemId") Long problemId);

    /**
     * 统计题目在有效题单中的有效关联数（problem_set_problem_rel.is_del=0 且 problem_set.is_del=0）。
     */
    long countActiveProblemSetRelationsByProblemId(@Param("problemId") Long problemId);

    /**
     * 统计题目的有效比赛提交关联数（contest_submission.is_del=0 且关联 contest/submission 未删除）。
     */
    long countActiveContestSubmissionsByProblemId(@Param("problemId") Long problemId);
}
