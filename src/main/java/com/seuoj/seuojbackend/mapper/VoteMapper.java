package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.Vote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VoteMapper extends BaseMapper<Vote> {

    /** 锁住这一行再改票，并发点击时按顺序处理 */
    @Select("SELECT * FROM vote WHERE target_type = #{targetType} AND target_id = #{targetId} AND user_id = #{userId} FOR UPDATE")
    Vote selectForUpdate(@Param("targetType") String targetType, @Param("targetId") Long targetId,
                         @Param("userId") Long userId);
}
