package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.Problem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目表 Mapper 接口
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

}