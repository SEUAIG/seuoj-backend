package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeOnlineSubmissionRequest;
import com.seuoj.seuojbackend.client.dto.JudgeOnlineSubmissionResponseData;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.dto.submission.OnlineJudgeSubmitDTO;
import com.seuoj.seuojbackend.dto.submission.SubmitDTO;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.SubmissionDetail;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.CodeStorageException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.InternalServerException;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.mapper.AssignmentMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionDetailMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.model.JudgeResultDetailItem;
import com.seuoj.seuojbackend.storage.CodeStorage;
import com.seuoj.seuojbackend.vo.me.HeatmapDayVO;
import com.seuoj.seuojbackend.vo.me.HeatmapSummaryVO;
import com.seuoj.seuojbackend.vo.me.MeHeatmapVO;
import com.seuoj.seuojbackend.vo.submission.OnlineJudgeResultVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionListItemVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionPageVO;
import com.seuoj.seuojbackend.vo.submission.SubmissionResultVO;
import com.seuoj.seuojbackend.vo.submission.SubmitVO;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private static final int MAX_JUDGE_DETAIL_LENGTH = 2000;
    private static final String JUDGE_DETAIL_TRUNCATED_SUFFIX = "...（内容已截断）";

    private final SubmissionMapper submissionMapper;
    private final SubmissionDetailMapper submissionDetailMapper;
    private final ProblemMapper problemMapper;
    private final AssignmentMapper assignmentMapper;
    private final JudgeClient judgeClient;
    private final CodeStorage codeStorage;
    private final UserInfoMapper userInfoMapper;
    private final UserRoleService userRoleService;
    private final PermissionService permissionService;
    private final ProblemService problemService;
    private final TransactionTemplate transactionTemplate;

    public SubmissionService(SubmissionMapper submissionMapper, SubmissionDetailMapper submissionDetailMapper,
            ProblemMapper problemMapper, AssignmentMapper assignmentMapper,
            JudgeClient judgeClient,
            CodeStorage codeStorage, UserInfoMapper userInfoMapper,
            UserRoleService userRoleService, PermissionService permissionService,
            ProblemService problemService,
            TransactionTemplate transactionTemplate) {
        this.submissionMapper = submissionMapper;
        this.submissionDetailMapper = submissionDetailMapper;
        this.problemMapper = problemMapper;
        this.assignmentMapper = assignmentMapper;
        this.judgeClient = judgeClient;
        this.codeStorage = codeStorage;
        this.userInfoMapper = userInfoMapper;
        this.userRoleService = userRoleService;
        this.permissionService = permissionService;
        this.problemService = problemService;
        this.transactionTemplate = transactionTemplate;
    }

    public OnlineJudgeResultVO submitOnlineJudge(OnlineJudgeSubmitDTO dto) {
        var userContext = UserContextHolder.get();
        if (userContext == null || userContext.getUserId() == null) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }
        Long userId = userContext.getUserId();

        Problem problem = problemService.getProblemByPidOrThrow(dto.getPid());
        permissionService.assertPermission(userId, ResourceType.PROBLEM, problem.getId(), PermissionOp.READ);

        JudgeOnlineSubmissionRequest request = new JudgeOnlineSubmissionRequest();
        request.setSubmissionId(UUID.randomUUID().toString());
        request.setPid(dto.getPid());
        request.setCode(dto.getCode());
        request.setLanguage(dto.getLanguage());

        List<JudgeOnlineSubmissionRequest.TestcaseItem> testcases = new ArrayList<>();
        for (OnlineJudgeSubmitDTO.TestcaseItem item : dto.getTestcases()) {
            JudgeOnlineSubmissionRequest.TestcaseItem testcase = new JudgeOnlineSubmissionRequest.TestcaseItem();
            testcase.setId(item.getId());
            testcase.setIn(item.getIn());
            testcases.add(testcase);
        }
        request.setTestcases(testcases);

        JudgeOnlineSubmissionResponseData response = judgeClient.submitOnline(request);

        OnlineJudgeResultVO vo = new OnlineJudgeResultVO();
        vo.setResultDetail(response.getResultDetail());
        vo.setStatus(response.getStatus());
        return vo;
    }

    /**
     * 提交代码进行评测
     *
     * @param dto 提交信息
     * @return 提交结果（包含 submissionNo）
     */
    public SubmitVO submit(SubmitDTO dto) {
        var userContext = UserContextHolder.get();
        if (userContext == null || userContext.getUserId() == null) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }
        Long userId = userContext.getUserId();

        Problem problem = problemService.getProblemByPidOrThrow(dto.getPid());

        if (dto.getAssignmentId() != null && dto.getProblemSetId() != null) {
            throw new BadRequestException("assignment_id 和 problem_set_id 不能同时传入");
        }

        if (!Boolean.TRUE.equals(problem.getIsPublic())) {
            if (dto.getAssignmentId() != null) {
                permissionService.assertProblemAccessViaAssignment(userId, problem.getId(), dto.getAssignmentId());
            } else if (dto.getProblemSetId() != null) {
                permissionService.assertProblemAccessViaProblemSet(userId, problem.getId(), dto.getProblemSetId());
            } else {
                throw new NotFoundException("题目不存在: " + dto.getPid());
            }
        }

        int codeBytes = dto.getCode() == null ? 0 : dto.getCode().getBytes(StandardCharsets.UTF_8).length;
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new BadRequestException("提交代码不能为空");
        }
        if (codeBytes > MAX_CODE_BYTES) {
            throw new BadRequestException("提交代码过长，请修改后重试");
        }

        Long assignmentId = dto.getAssignmentId();
        if (assignmentId != null) {
            Assignment assignment = assignmentMapper.selectById(assignmentId);
            if (assignment == null) {
                throw new NotFoundException("作业不存在");
            }
            if (!"PUBLISHED".equals(assignment.getStatus())) {
                throw new BadRequestException("作业未发布，不可提交");
            }
            LocalDateTime now = LocalDateTime.now();
            if (assignment.getVisibleFrom() != null && now.isBefore(assignment.getVisibleFrom())) {
                throw new BadRequestException("作业尚未开放");
            }
            if (assignment.getVisibleTo() != null && now.isAfter(assignment.getVisibleTo())) {
                throw new BadRequestException("作业已关闭");
            }
            if (assignment.getDeadline() != null && now.isAfter(assignment.getDeadline())) {
                throw new BadRequestException("作业已截止，不可提交");
            }
        }

        // 数据库操作（事务完成）
        Submission submission = submitInTransaction(dto, userId, problem.getId());
        if (submission == null) {
            throw new InternalServerException("提交失败");
        }

        // 发送评测请求放到事务外完成
        sendToJudgeServer(submission.getSubmissionNo(), dto.getPid(), dto.getCode(), dto.getLanguage());

        SubmitVO submitVO = new SubmitVO();
        submitVO.setSubmissionNo(submission.getSubmissionNo());
        return submitVO;
    }

    /**
     * 事务内完成校验、记录创建、代码保存、计数累加，并注册 afterCommit/rollback 处理
     */
    private Submission submitInTransaction(SubmitDTO dto, Long userId, Long problemId) {
        return transactionTemplate.execute(status -> {
            // 创建提交记录
            Submission submission = new Submission();
            submission.setSubmissionNo(UUID.randomUUID().toString());
            submission.setUserId(userId);
            submission.setProblemId(problemId);
            submission.setLanguage(dto.getLanguage());
            submission.setAssignmentId(dto.getAssignmentId());
            submission.setStatus(SubmissionStatus.PENDING.getStatus());
            submission.setSubmitTime(LocalDateTime.now());
            submissionMapper.insert(submission);

            // 保存用户代码
            codeStorage.save(dto.getCode(), submission.getSubmissionNo());
            // 若后续回滚，补偿删除已写入的代码文件，避免孤儿文件
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

            // 累加题目提交总数
            problemMapper.atomicallyIncreaseTotalSubmissionCount(problemId);
            return submission;
        });
    }

    /**
     * 向评测机发送评测请求（带异常处理）
     */
    private void sendToJudgeServer(String submissionNo, String pid, String code, String language) {
        JudgeSubmissionRequest requestBody = new JudgeSubmissionRequest(submissionNo, pid, code, language);
        try {
            judgeClient.submit(requestBody);
            submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                    .set(Submission::getStatus, SubmissionStatus.RUNNING.getStatus())
                    .eq(Submission::getSubmissionNo, submissionNo)
                    .eq(Submission::getStatus, SubmissionStatus.PENDING.getStatus()));
        } catch (JudgeRemoteException e) {
            log.error("评测端提交失败, submissionNo={}", submissionNo, e);
            submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                    .set(Submission::getStatus, SubmissionStatus.FAILED.getStatus())
                    .eq(Submission::getSubmissionNo, submissionNo)
                    .eq(Submission::getStatus, SubmissionStatus.PENDING.getStatus()));
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

        // 查询问题
        Problem problem = problemMapper.selectById(submission.getProblemId());
        if (problem == null) {
            throw new NotFoundException("题目不存在: " + submission.getProblemId());
        }

        // 校验记录是否属于自己
        if (!Objects.equals(submission.getUserId(), userId) && !isTeacherOrAdmin(userId)) {
            throw new ForbiddenException("无权查看他人提交记录");
        }

        SubmissionDetail detail = submissionDetailMapper.selectById(submission.getId());
        return convertToResultVO(submission, problem, detail);
    }

    /**
     * 分页查询当前用户的提交记录
     *
     * @param current 当前页（从 1 开始）
     * @param size    每页条数
     * @return 提交记录分页
     */
    public SubmissionPageVO listSubmissions(Integer current, Integer size, Long userId, String verdict,
            Long assignmentId, String pid, String language, String username) {
        if (current == null || current < 1) {
            throw new BadRequestException("页码必须大于等于 1");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BadRequestException("每页条数必须在 1 到 100 之间");
        }

        Long effectiveUserId = userId;
        if (effectiveUserId == null) {
            var userContext = UserContextHolder.get();
            if (userContext != null && userContext.getUserId() != null) {
                Long currentUserId = userContext.getUserId();
                if (!userRoleService.isTeacherOrAdmin(currentUserId)) {
                    effectiveUserId = currentUserId;
                }
            }
        }

        Page<SubmissionListItemVO> page = new Page<>(current, size);
        IPage<SubmissionListItemVO> pageResult = submissionMapper.selectSubmissionPage(
                page, effectiveUserId, verdict, assignmentId, pid, language, username);

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

    private SubmissionResultVO convertToResultVO(Submission submission, Problem problem, SubmissionDetail detail) {
        SubmissionResultVO vo = new SubmissionResultVO();
        vo.setSubmissionNo(submission.getSubmissionNo());
        vo.setPid(problem != null ? problem.getPid() : null);
        vo.setLanguage(submission.getLanguage());
        vo.setStatus(submission.getStatus());
        vo.setVerdict(submission.getVerdict());
        vo.setScore(submission.getScore());
        vo.setSubmitTime(submission.getSubmitTime());
        vo.setFinishTime(submission.getFinishTime());

        if (detail != null) {
            vo.setResultDetail(truncateResultDetail(detail.getResultDetail()));
            vo.setSubtasks(detail.getSubtasks());
            vo.setErrorDetail(truncateJudgeDetail(detail.getErrorDetail()));
        }

        try {
            vo.setCode(codeStorage.getCode(submission.getSubmissionNo()));
        } catch (CodeStorageException e) {
            log.warn("代码文件缺失 submissionNo={}", submission.getSubmissionNo());
            vo.setCode(null);
        }
        vo.setUserId(submission.getUserId());
        UserInfo user = userInfoMapper.selectById(submission.getUserId());
        vo.setUsername(user != null ? user.getUsername() : null);
        vo.setNickname(user != null ? user.getNickname() : null);
        return vo;
    }

    private boolean isTeacherOrAdmin(Long userId) {
        return userRoleService != null && userRoleService.isTeacherOrAdmin(userId);
    }

    private String truncateJudgeDetail(String raw) {
        if (raw == null || raw.length() <= MAX_JUDGE_DETAIL_LENGTH) {
            return raw;
        }
        int keepLength = MAX_JUDGE_DETAIL_LENGTH - JUDGE_DETAIL_TRUNCATED_SUFFIX.length();
        return raw.substring(0, keepLength) + JUDGE_DETAIL_TRUNCATED_SUFFIX;
    }

    private List<JudgeResultDetailItem> truncateResultDetail(List<JudgeResultDetailItem> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            return rawItems;
        }
        List<JudgeResultDetailItem> result = new ArrayList<>(rawItems.size());
        for (JudgeResultDetailItem item : rawItems) {
            if (item == null) {
                result.add(null);
                continue;
            }
            JudgeResultDetailItem copy = new JudgeResultDetailItem();
            copy.setId(item.getId());
            copy.setIn(truncateJudgeDetail(item.getIn()));
            copy.setOut(truncateJudgeDetail(item.getOut()));
            copy.setAns(truncateJudgeDetail(item.getAns()));
            copy.setSys(truncateJudgeDetail(item.getSys()));
            copy.setTime(item.getTime());
            copy.setMem(item.getMem());
            copy.setType(item.getType());
            copy.setScore(item.getScore());
            result.add(copy);
        }
        return result;
    }
}
