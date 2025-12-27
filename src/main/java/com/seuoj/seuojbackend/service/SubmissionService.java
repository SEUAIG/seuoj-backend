package com.seuoj.seuojbackend.service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.dto.submission.SubmitDTO;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.storage.CodeStorage;
import com.seuoj.seuojbackend.util.TransactionUtil;
import com.seuoj.seuojbackend.vo.submission.SubmissionResultVO;
import com.seuoj.seuojbackend.vo.submission.SubmitVO;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SubmissionService {

    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final JudgeClient judgeClient;
    private final CodeStorage codeStorage;
    private final UserInfoMapper userInfoMapper;

    public SubmissionService(SubmissionMapper submissionMapper, ProblemMapper problemMapper, JudgeClient judgeClient, CodeStorage codeStorage, UserInfoMapper userInfoMapper) {
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
        this.codeStorage = codeStorage;
        this.userInfoMapper = userInfoMapper;
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
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>().eq(Problem::getPid, dto.getPid()));
        if (problem == null) {
            throw new NotFoundException("提交过程中发现题目不存在:" + dto.getPid());
        }

        // 判断用户提交代码是否异常（过大）
        if (dto.getCode().length() > 65535) {
            throw new BadRequestException("提交代码过长，请修改后重试");
        }

        // 创建提交记录
        Submission submission = new Submission();
        submission.setSubmissionNo(UUID.randomUUID().toString());
        submission.setUserId(userId);
        submission.setProblemId(problem.getId());
        submission.setLanguage(dto.getLanguage());
        submission.setStatus(SubmissionStatus.PENDING.getStatus()); // 初始状态：等待评测
        submission.setSubmitTime(LocalDateTime.now());
        submissionMapper.insert(submission);

        // TODO: 这里的异常处理逻辑和事务的关系？
        // 保存用户代码
        codeStorage.save(dto.getCode(), submission.getSubmissionNo());

        // 原子增加提交总数
        problemMapper.atomicallyIncreaseTotalSubmissionCount(problem.getId());

        // 向评测机发送请求（放到事务外完成）
        TransactionUtil.registerAfterCommit(() -> {
            this.sendToJudgeServer(submission.getSubmissionNo(), dto.getPid(), dto.getCode(), dto.getLanguage());
        });

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
            submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                    .set(Submission::getStatus, SubmissionStatus.RUNNING.getStatus())
                    .eq(Submission::getSubmissionNo, submissionNo));
        } catch (JudgeRemoteException e) {
            log.error("向评测端提交评测失败  - submissionNo: {}, error: {}", submissionNo, e.getMessage());
            // TODO: 更具体的处理 比如重试机制

            // 更新提交状态为失败
            submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                    .set(Submission::getStatus, SubmissionStatus.FAILED.getStatus())
                    .eq(Submission::getSubmissionNo, submissionNo));
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
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getSubmissionNo, submissionNo));

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
        vo.setVerdict(submission.getVerdict());
        vo.setResultDetail(submission.getResultDetail());
        vo.setErrorDetail(submission.getErrorDetail());
        vo.setSubmitTime(submission.getSubmitTime());
        vo.setFinishTime(submission.getFinishTime());

        // 查询用户代码
        vo.setCode(codeStorage.getCode(submission.getSubmissionNo()));

        // 查询用户名
        UserInfo user = userInfoMapper.selectById(submission.getUserId());
        vo.setUsername(user != null ? user.getUsername() : null);
        return vo;
    }

}
