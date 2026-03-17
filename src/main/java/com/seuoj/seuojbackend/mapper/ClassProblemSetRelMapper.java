package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.ClassProblemSetRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClassProblemSetRelMapper extends BaseMapper<ClassProblemSetRel> {

    /**
     * 恢复最近一条被软删除的班级题单关联
     *
     * @param classId 班级ID
     * @param problemSetId 题单ID
     * @return 受影响行数
     */
    int restoreDeletedProblemSetLink(@Param("classId") Long classId, @Param("problemSetId") Long problemSetId);
}

