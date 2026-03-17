package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.ClassContestRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClassContestRelMapper extends BaseMapper<ClassContestRel> {

    /**
     * 恢复最近一条被软删除的班级比赛关联
     *
     * @param classId 班级ID
     * @param contestId 比赛ID
     * @return 受影响行数
     */
    int restoreDeletedContestLink(@Param("classId") Long classId, @Param("contestId") Long contestId);
}

