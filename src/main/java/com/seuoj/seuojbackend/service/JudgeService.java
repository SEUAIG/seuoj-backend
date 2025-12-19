package com.seuoj.seuojbackend.service;

import java.time.LocalDateTime;

import com.seuoj.seuojbackend.common.SubmissionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seuoj.seuojbackend.dto.judge.JudgeResultDTO;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;


@Service
public class JudgeService {

    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;

    public JudgeService(SubmissionMapper submissionMapper, ProblemMapper problemMapper) {
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
    }

    /**
     * 处理评测结果回调
     *
     * @param dto 评测结果
     */
    @Transactional
    public void handleJudgeResult(JudgeResultDTO dto, String submissionNo) {
        // TODO:检验请求来源

        Submission submission = submissionMapper.selectOne(
                new QueryWrapper<Submission>()
                        .eq("submission_no", submissionNo));

        if (submission == null) {
            throw new NotFoundException("提交记录不存在: " + submissionNo);
        }

        // 校验状态合法性（防止重复回调）
        if (!SubmissionStatus.PENDING.getStatus().equals(submission.getStatus()) &&
                !SubmissionStatus.RUNNING.getStatus().equals(submission.getStatus())) {
            throw new BadRequestException("该提交已经完成评测，无法更新");
        }

        // 更新提交记录
        submission.setStatus(dto.getStatus());
        submission.setResultDetail(dto.getResultDetail());
        submission.setFinishTime(LocalDateTime.now());
        submissionMapper.updateById(submission);

        // 如果通过，更新题目通过数
        if (SubmissionStatus.AC.getStatus().equals(dto.getStatus())) {
            Problem problem = problemMapper.selectById(submission.getProblemId());
            if (problem != null) {
                problem.setTotalAccept(problem.getTotalAccept() + 1);
                problemMapper.updateById(problem);
            }
        }
    }

}
