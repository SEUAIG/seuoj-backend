package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.vo.me.HeatmapDayVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


/**
 * 用户提交与评测结果表 Mapper 接口
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

    /**
     * 分页查询当前用户提交记录（联表避免 N+1）
     */
    IPage<SubmissionListItemVO> selectSubmissionPage(Page<?> page,
                                                     @Param("userId") Long userId,
                                                     @Param("verdict") String verdict,
                                                     @Param("assignmentId") Long assignmentId,
                                                     @Param("pid") String pid,
                                                     @Param("language") String language,
                                                     @Param("username") String username);

    IPage<SubmissionListItemVO> selectAssignmentSubmissionPage(Page<?> page,
                                                               @Param("problemIds") List<Long> problemIds,
                                                               @Param("classId") Long classId);

    List<HeatmapDayVO> selectUserHeatmapDays(@Param("userId") Long userId, @Param("year") Integer year);
}
