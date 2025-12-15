package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.Submission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户提交与评测结果表 Mapper 接口
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

}