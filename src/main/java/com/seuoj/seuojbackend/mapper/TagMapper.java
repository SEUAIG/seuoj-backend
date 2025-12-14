package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目标签表 Mapper 接口
 *
 * @author YourName
 * @since 2025-12-13
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

}