package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.ProblemTagRel;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 题目标签关联表 Mapper 接口
 */
@Mapper
public interface ProblemTagRelMapper extends BaseMapper<ProblemTagRel> {

    int insertBatch(List<ProblemTagRel> rels);

    int markAllDeletedByProblemId(@Param("problemId") Long problemId);

    int restoreByProblemIdAndTagIds(@Param("problemId") Long problemId, @Param("tagIds") Set<Long> tagIds);

    List<Long> selectTagIdsByProblemIdAndTagIds(@Param("problemId") Long problemId, @Param("tagIds") Set<Long> tagIds);
}
