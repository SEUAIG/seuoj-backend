package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目标签表 Mapper 接口
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

}