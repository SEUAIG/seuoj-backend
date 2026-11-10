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

    @org.apache.ibatis.annotations.Select("SELECT * FROM contest WHERE id=#{id} AND is_del=0 FOR UPDATE")
    Contest lockById(@Param("id") Long id);

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

    /** 各条件为空表示不限：userId 限定提交者；pid 限定题目；result 为 accepted/rejected；username 按账号名 */
    IPage<ContestSubmissionRecordVO> selectContestSubmissionPage(
            Page<?> page, @Param("contestId") Long contestId, @Param("userId") Long userId,
            @Param("pid") String pid, @Param("result") String result, @Param("username") String username);

    List<Submission> selectContestFinishedSubmissions(@Param("contestId") Long contestId);

    List<UserInfo> selectContestRegisteredUsers(@Param("contestId") Long contestId);
}
