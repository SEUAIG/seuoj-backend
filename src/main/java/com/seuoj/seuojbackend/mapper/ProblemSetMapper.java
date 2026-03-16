package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.entity.ProblemSet;
import com.seuoj.seuojbackend.vo.problemset.ProblemSetItemVO;
import com.seuoj.seuojbackend.vo.problemset.ProblemSetProblemItemVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 题单数据访问接口
 */
@Mapper
public interface ProblemSetMapper extends BaseMapper<ProblemSet> {

    /**
     * 分页查询题单列表
     */
    IPage<ProblemSetItemVO> selectProblemSetPage(
            Page<?> page,
            @Param("userId") Long userId,
            @Param("isAdmin") boolean isAdmin);

    /**
     * 查询题单题目列表
     */
    List<ProblemSetProblemItemVO> selectProblemSetProblems(@Param("problemSetId") Long problemSetId);
}
