package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.ClassStudentRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClassStudentRelMapper extends BaseMapper<ClassStudentRel> {

    int restoreDeletedStudent(@Param("classId") Long classId, @Param("userId") Long userId);
}

