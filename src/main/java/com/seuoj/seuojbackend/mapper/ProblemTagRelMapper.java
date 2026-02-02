package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.ProblemTagRel;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目标签关联表 Mapper 接口
 */
@Mapper
public interface ProblemTagRelMapper extends BaseMapper<ProblemTagRel> {

    int insertBatch(List<ProblemTagRel> rels);
}
