package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.vo.classinfo.ClassItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberItemVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageItemVO;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClassInfoMapper extends BaseMapper<ClassInfo> {

    /**
     * 分页查询班级列表
     */
    IPage<ClassItemVO> selectClassPage(
            Page<?> page,
            @Param("userId") Long userId,
            @Param("isAdmin") boolean isAdmin);

    /**
     * 分页查询班级成员
     */
    IPage<ClassMemberItemVO> selectClassMemberPage(Page<?> page, @Param("classId") Long classId);

    /**
     * 分页查询班级关联题单
     */
    IPage<LinkPageItemVO> selectClassProblemSetPage(Page<?> page, @Param("classId") Long classId);

    /**
     * 分页查询班级关联比赛
     */
    IPage<LinkPageItemVO> selectClassContestPage(Page<?> page, @Param("classId") Long classId);

    /**
     * 查询班级各题单的题目数与 AC 统计（概览用）
     */
    List<Map<String, Object>> selectClassOverviewStats(@Param("classId") Long classId);

    /**
     * 查询班级每个学生在所有关联题单中的 AC 题数
     */
    List<Map<String, Object>> selectClassStudentAcStats(@Param("classId") Long classId);

    /**
     * 查询班级题单矩阵原始数据（学生×题目×verdict）
     */
    List<Map<String, Object>> selectClassProblemSetMatrixRaw(
            @Param("classId") Long classId,
            @Param("problemSetId") Long problemSetId);
}
