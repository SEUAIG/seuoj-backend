package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.Tag;
import com.seuoj.seuojbackend.vo.tag.TagGroupTagRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目标签表Mapper接口
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 获取标签及其分组（单次查询）
     *
     * @return 分组与标签行数据
     */
    List<TagGroupTagRow> listTagWithGroup();
}
