package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}