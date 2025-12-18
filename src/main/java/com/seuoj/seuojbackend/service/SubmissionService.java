package com.seuoj.seuojbackend.service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.dto.submission.SubmitDTO;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.vo.submission.SubmissionResultVO;
import com.seuoj.seuojbackend.vo.submission.SubmitVO;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final JudgeClient judgeClient;

    public SubmissionService(SubmissionMapper submissionMapper, ProblemMapper problemMapper, JudgeClient judgeClient) {
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
    }

    /**
     * 提交代码进行评测
     *
     * @param dto 提交信息
     * @return 提交结果（包含 submissionNo）
     */
    @Transactional
    public SubmitVO submit(SubmitDTO dto) {

        // 校验
        Long userId = UserContextHolder.get().getUserId();
        if (userId == null) {
            throw new NotFoundException("提交过程中发现用户未登录");
        }
        Problem problem = problemMapper.selectOne(new QueryWrapper<Problem>().eq("pid", dto.getPid()));
        if (problem == null) {
            throw new NotFoundException("提交过程中发现题目不存在:" + dto.getPid());
        }
        // 创建提交记录
        Submission submission = new Submission();
        submission.setSubmissionNo(UUID.randomUUID().toString());
        submission.setUserId(userId);
        submission.setProblemId(problem.getId());
        submission.setLanguage(dto.getLanguage());
        submission.setStatus("PENDING"); // 初始状态：等待评测
        submission.setSubmitTime(LocalDateTime.now());
        submissionMapper.insert(submission);

        problem.setTotalSubmit(problem.getTotalSubmit() + 1);
        problemMapper.updateById(problem);

        // 向评测机发送请求（异步：评测机收到后立即返回，后台慢慢评测）
        sendToJudgeServer(submission.getSubmissionNo(), dto.getPid(), dto.getCode(), dto.getLanguage());

        SubmitVO submitVO = new SubmitVO();
        submitVO.setSubmissionNo(submission.getSubmissionNo());
        return submitVO;
    }

    /**
     * 向评测机发送评测请求
     */
    private void sendToJudgeServer(String submissionNo, String pid, String code, String language) {
        log.info("Send judge request submissionNo={}, pid={}", submissionNo, pid);
        JudgeSubmissionRequest requestBody = new JudgeSubmissionRequest(submissionNo, pid, code, language);
        try {
            judgeClient.submit(requestBody);
        } catch (JudgeRemoteException e) {
            log.error("Judge server submission request failed - submissionNo: {}, error: {}", submissionNo, e.getMessage());
            // Keep record as PENDING so user can check status later.
        }
    }

    /**
     * 根据提交编号查询评测结果
     *
     * @param submissionNo 提交记录业务编号
     * @return 评测结果
     */
    public SubmissionResultVO getResult(String submissionNo) {
        // 检验查询
        Long userId = UserContextHolder.get().getUserId();
        if (userId == null) {
            throw new NotFoundException("查询提交: 用户未登录");
        }

        Submission submission = submissionMapper.selectOne(
                new QueryWrapper<Submission>()
                        .eq("submission_no", submissionNo));

        if (submission == null) {
            throw new NotFoundException("查询提交: 提交记录不存在: " + submissionNo);
        }
        // 查询问题
        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem == null) {
            throw new NotFoundException("查询提交: 问题不存在: " + submission.getProblemId());
        }
        // 校验记录是否属于自己
        if (!Objects.equals(submission.getUserId(), userId)) {
            throw new BadRequestException("不可查询不属于自己的测评记录信息");
        }

        return convertToResultVO(submission, problem);
    }

    private SubmissionResultVO convertToResultVO(Submission submission, Problem problem) {
        SubmissionResultVO vo = new SubmissionResultVO();
        vo.setSubmissionNo(submission.getSubmissionNo());
        vo.setPid(problem != null ? problem.getPid() : null);
        vo.setLanguage(submission.getLanguage());
        vo.setStatus(submission.getStatus());
        vo.setResultDetail(submission.getResultDetail());
        vo.setSubmitTime(submission.getSubmitTime());
        vo.setFinishTime(submission.getFinishTime());
        return vo;
    }

}