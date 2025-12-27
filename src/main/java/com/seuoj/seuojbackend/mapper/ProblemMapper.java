package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
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
}