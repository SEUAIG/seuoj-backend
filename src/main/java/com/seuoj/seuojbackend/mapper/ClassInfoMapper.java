package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.entity.ClassInfo;
import com.seuoj.seuojbackend.vo.classinfo.AssignmentOverviewVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassMemberItemVO;
import com.seuoj.seuojbackend.vo.classinfo.ClassOverviewVO;
import com.seuoj.seuojbackend.vo.classinfo.LinkPageItemVO;
import java.util.List;
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
     * 分页查询班级关联比赛
     */
    IPage<LinkPageItemVO> selectClassContestPage(Page<?> page, @Param("classId") Long classId);

    List<ClassOverviewVO.StudentOverviewItem> selectStudentOverview(@Param("classId") Long classId);

    List<ClassOverviewVO.AssignmentProgressItem> selectAssignmentProgress(@Param("classId") Long classId);

    Integer selectTotalProblems(@Param("classId") Long classId);

    List<AssignmentOverviewVO.ProblemStatItem> selectAssignmentProblemStats(
            @Param("classId") Long classId,
            @Param("assignmentId") Long assignmentId);

    List<AssignmentOverviewVO.StudentStatItem> selectAssignmentStudentStats(
            @Param("classId") Long classId,
            @Param("assignmentId") Long assignmentId,
            @Param("deadline") java.time.LocalDateTime deadline);
}

