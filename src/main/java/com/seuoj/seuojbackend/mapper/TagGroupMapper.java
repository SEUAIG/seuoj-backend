package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.TagGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签分组数据访问层
 */
@Mapper
public interface TagGroupMapper extends BaseMapper<TagGroup> {
}