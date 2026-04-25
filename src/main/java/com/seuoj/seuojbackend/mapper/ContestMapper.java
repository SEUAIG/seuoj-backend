package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.vo.contest.ContestPageItemVO;
import com.seuoj.seuojbackend.vo.contest.ContestProblemOverviewVO;
import com.seuoj.seuojbackend.vo.contest.ContestSubmissionRecordVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContestMapper extends BaseMapper<Contest> {

    IPage<ContestPageItemVO> selectContestPage(
            Page<?> page,
            @Param("userId") Long userId,
            @Param("isAdmin") boolean isAdmin,
            @Param("isTeacher") boolean isTeacher,
            @Param("status") String status,
            @Param("titleKeyword") String titleKeyword,
            @Param("ruleType") String ruleType,
            @Param("startTimeFrom") LocalDateTime startTimeFrom,
            @Param("endTimeTo") LocalDateTime endTimeTo);

    List<ContestProblemOverviewVO> selectContestProblems(@Param("contestId") Long contestId);

    IPage<ContestSubmissionRecordVO> selectContestSubmissionPage(
            Page<?> page, @Param("contestId") Long contestId);

    List<Submission> selectContestFinishedSubmissions(@Param("contestId") Long contestId);

    List<UserInfo> selectContestRegisteredUsers(@Param("contestId") Long contestId);
}
