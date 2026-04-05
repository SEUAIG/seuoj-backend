package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.dto.submission.SubmitDTO;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.CodeStorageException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.InternalServerException;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.storage.CodeStorage;
import com.seuoj.seuojbackend.vo.me.HeatmapDayVO;
import com.seuoj.seuojbackend.vo.me.HeatmapSummaryVO;
import com.seuoj.seuojbackend.vo.me.MeHeatmapVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionListItemVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionPageVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionResultVO;
import com.seuoj.seuojbackend.vo.submission.SubmitVO;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class SubmissionService {

    private static final int MAX_CODE_BYTES = 65535;

    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final JudgeClient judgeClient;
    private final CodeStorage codeStorage;
    private final UserInfoMapper userInfoMapper;
    private final UserRoleService userRoleService;
    private final TransactionTemplate transactionTemplate;

    public SubmissionService(SubmissionMapper submissionMapper, ProblemMapper problemMapper, JudgeClient judgeClient,
                             CodeStorage codeStorage, UserInfoMapper userInfoMapper,
                             UserRoleService userRoleService, TransactionTemplate transactionTemplate) {
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
        this.codeStorage = codeStorage;
        this.userInfoMapper = userInfoMapper;
        this.userRoleService = userRoleService;
        this.transactionTemplate = transactionTemplate;
    }

    public SubmitVO submit(SubmitDTO dto) {
        var userContext = UserContextHolder.get();
        if (userContext == null || userContext.getUserId() == null) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }
        Long userId = userContext.getUserId();

        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, dto.getPid())
                .eq(Problem::getIsPublic, true));
        if (problem == null) {
            throw new NotFoundException("题目不存在: " + dto.getPid());
        }

        int codeBytes = dto.getCode() == null ? 0 : dto.getCode().getBytes(StandardCharsets.UTF_8).length;
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new BadRequestException("提交代码不能为空");
        }
        if (codeBytes > MAX_CODE_BYTES) {
            throw new BadRequestException("提交代码过长，请修改后重试");
        }

        Submission submission = submitInTransaction(dto, userId, problem.getId());
        if (submission == null) {
            throw new InternalServerException("提交失败");
        }

        sendToJudgeServer(submission.getSubmissionNo(), dto.getPid(), dto.getCode(), dto.getLanguage());

        SubmitVO submitVO = new SubmitVO();
        submitVO.setSubmissionNo(submission.getSubmissionNo());
        return submitVO;
    }

    private Submission submitInTransaction(SubmitDTO dto, Long userId, Long problemId) {
        return transactionTemplate.execute(status -> {
            Submission submission = new Submission();
            submission.setSubmissionNo(UUID.randomUUID().toString());
            submission.setUserId(userId);
            submission.setProblemId(problemId);
            submission.setLanguage(dto.getLanguage());
            submission.setStatus(SubmissionStatus.PENDING.getStatus());
            submission.setSubmitTime(LocalDateTime.now());
            submissionMapper.insert(submission);

            codeStorage.save(dto.getCode(), submission.getSubmissionNo());
            String submissionNo = submission.getSubmissionNo();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int txStatus) {
                        if (txStatus == TransactionSynchronization.STATUS_ROLLED_BACK) {
                            try {
                                codeStorage.delete(submissionNo);
                            } catch (CodeStorageException e) {
                                log.warn("rollback cleanup failed, submissionNo={}", submissionNo, e);
                            }
                        }
                    }
                });
            }

            problemMapper.atomicallyIncreaseTotalSubmissionCount(problemId);
            return submission;
        });
    }

    private void sendToJudgeServer(String submissionNo, String pid, String code, String language) {
        JudgeSubmissionRequest requestBody = new JudgeSubmissionRequest(submissionNo, pid, code, language);
        try {
            judgeClient.submit(requestBody);
            submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                    .set(Submission::getStatus, SubmissionStatus.RUNNING.getStatus())
                    .eq(Submission::getSubmissionNo, submissionNo)
                    .eq(Submission::getStatus, SubmissionStatus.PENDING.getStatus()));
        } catch (JudgeRemoteException e) {
            log.error("judge submit failed, submissionNo={}", submissionNo, e);
            submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                    .set(Submission::getStatus, SubmissionStatus.FAILED.getStatus())
                    .eq(Submission::getSubmissionNo, submissionNo)
                    .eq(Submission::getStatus, SubmissionStatus.PENDING.getStatus()));
        }
    }

    public SubmissionResultVO getResult(String submissionNo) {
        var userContext = UserContextHolder.get();
        if (userContext == null || userContext.getUserId() == null) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }
        Long userId = userContext.getUserId();

        Submission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<Submission>().eq(Submission::getSubmissionNo, submissionNo));
        if (submission == null) {
            throw new NotFoundException("提交记录不存在: " + submissionNo);
        }

        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem == null) {
            throw new NotFoundException("题目不存在: " + submission.getProblemId());
        }

        if (!Objects.equals(submission.getUserId(), userId) && !isAdmin(userId)) {
            throw new ForbiddenException("无权查看他人提交记录");
        }

        return convertToResultVO(submission, problem);
    }

    public SubmissionPageVO listSubmissions(Integer current, Integer size) {
        if (current == null || current < 1) {
            throw new BadRequestException("页码必须大于等于 1");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BadRequestException("每页条数必须在 1 到 100 之间");
        }

        var userContext = UserContextHolder.get();
        if (userContext == null || userContext.getUserId() == null) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }
        Long userId = userContext.getUserId();

        Page<SubmissionListItemVO> page = new Page<>(current, size);
        IPage<SubmissionListItemVO> pageResult = isAdmin(userId)
                ? submissionMapper.selectAllSubmissionPage(page)
                : submissionMapper.selectUserSubmissionPage(page, userId);

        SubmissionPageVO result = new SubmissionPageVO();
        result.setCurrent(pageResult.getCurrent());
        result.setSize(pageResult.getSize());
        result.setTotal(pageResult.getTotal());
        result.setRecords(pageResult.getRecords());
        return result;
    }

    public MeHeatmapVO getHeatmap(String year) {
        var userContext = UserContextHolder.get();
        if (userContext == null || userContext.getUserId() == null) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }
        Long userId = userContext.getUserId();

        List<HeatmapDayVO> days = submissionMapper.selectUserHeatmapDays(userId, Integer.parseInt(year));
        long total = days.stream()
                .map(HeatmapDayVO::getCount)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        HeatmapSummaryVO summary = new HeatmapSummaryVO();
        summary.setTotal(total);
        summary.setActiveDays(days.size());

        MeHeatmapVO result = new MeHeatmapVO();
        result.setYear(year);
        result.setDays(days);
        result.setSummary(summary);
        return result;
    }

    private SubmissionResultVO convertToResultVO(Submission submission, Problem problem) {
        SubmissionResultVO vo = new SubmissionResultVO();
        vo.setSubmissionNo(submission.getSubmissionNo());
        vo.setPid(problem != null ? problem.getPid() : null);
        vo.setLanguage(submission.getLanguage());
        vo.setStatus(submission.getStatus());
        vo.setVerdict(submission.getVerdict());
        vo.setResultDetail(submission.getResultDetail());
        vo.setSubtasks(submission.getSubtasks());
        vo.setErrorDetail(submission.getErrorDetail());
        vo.setScore(submission.getScore());
        vo.setSubmitTime(submission.getSubmitTime());
        vo.setFinishTime(submission.getFinishTime());
        vo.setCode(codeStorage.getCode(submission.getSubmissionNo()));

        UserInfo user = userInfoMapper.selectById(submission.getUserId());
        vo.setUsername(user != null ? user.getUsername() : null);
        return vo;
    }

    private boolean isAdmin(Long userId) {
        return userRoleService != null && userRoleService.isAdmin(userId);
    }
}
