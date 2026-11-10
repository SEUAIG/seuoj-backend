package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.ContestSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContestSubmissionMapper extends BaseMapper<ContestSubmission> {
    // Include soft-deleted associations: removing a contest must not turn its submissions into public practice results.
    @Select("SELECT contest_id FROM contest_submission WHERE submission_id = #{submissionId} ORDER BY id LIMIT 1")
    Long findContestIdBySubmissionId(@Param("submissionId") Long submissionId);
}

