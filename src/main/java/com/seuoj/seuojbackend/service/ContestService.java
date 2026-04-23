package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.common.ContestStatus;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.dto.contest.ContestCreateDTO;
import com.seuoj.seuojbackend.dto.contest.ContestProblemEditDTO;
import com.seuoj.seuojbackend.dto.contest.ContestSubmitDTO;
import com.seuoj.seuojbackend.dto.contest.ContestUpdateDTO;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ContestProblemRel;
import com.seuoj.seuojbackend.entity.ContestRegisterRel;
import com.seuoj.seuojbackend.entity.ContestSubmission;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.SubmissionDetail;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.CodeStorageException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.InternalServerException;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ContestMapper;
import com.seuoj.seuojbackend.mapper.ContestProblemRelMapper;
import com.seuoj.seuojbackend.mapper.ContestRegisterRelMapper;
import com.seuoj.seuojbackend.mapper.ContestSubmissionMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionDetailMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.service.contest.ContestRankingStrategyFactory;
import com.seuoj.seuojbackend.storage.CodeStorage;
import com.seuoj.seuojbackend.vo.contest.ContestCreateVO;
import com.seuoj.seuojbackend.vo.contest.ContestDetailVO;
import com.seuoj.seuojbackend.vo.contest.ContestPageItemVO;
import com.seuoj.seuojbackend.vo.contest.ContestPageVO;
import com.seuoj.seuojbackend.vo.contest.ContestProblemListInEditVO;
import com.seuoj.seuojbackend.vo.contest.ContestProblemOverviewVO;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsRecordVO;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsVO;
import com.seuoj.seuojbackend.vo.contest.ContestSubmissionDetailVO;
import com.seuoj.seuojbackend.vo.contest.ContestSubmissionPageVO;
import com.seuoj.seuojbackend.vo.contest.ContestSubmissionRecordVO;
import com.seuoj.seuojbackend.vo.contest.ContestSubmitVO;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ContestService {

    private static final int MAX_CODE_BYTES = 65535;

    private final ContestMapper contestMapper;
    private final ContestProblemRelMapper contestProblemRelMapper;
    private final ContestRegisterRelMapper contestRegisterRelMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionDetailMapper submissionDetailMapper;
    private final ProblemMapper problemMapper;
    private final UserInfoMapper userInfoMapper;
    private final PermissionService permissionService;
    private final UserRoleService userRoleService;
    private final JudgeClient judgeClient;
    private final CodeStorage codeStorage;
    private final TransactionTemplate transactionTemplate;
    private final ContestRankingStrategyFactory rankingStrategyFactory;

    public ContestService(ContestMapper contestMapper,
                          ContestProblemRelMapper contestProblemRelMapper,
                          ContestRegisterRelMapper contestRegisterRelMapper,
                          ContestSubmissionMapper contestSubmissionMapper,
                          SubmissionMapper submissionMapper,
                          SubmissionDetailMapper submissionDetailMapper,
                          ProblemMapper problemMapper,
                          UserInfoMapper userInfoMapper,
                          PermissionService permissionService,
                          UserRoleService userRoleService,
                          JudgeClient judgeClient,
                          CodeStorage codeStorage,
                          TransactionTemplate transactionTemplate,
                          ContestRankingStrategyFactory rankingStrategyFactory) {
        this.contestMapper = contestMapper;
        this.contestProblemRelMapper = contestProblemRelMapper;
        this.contestRegisterRelMapper = contestRegisterRelMapper;
        this.contestSubmissionMapper = contestSubmissionMapper;
        this.submissionMapper = submissionMapper;
        this.submissionDetailMapper = submissionDetailMapper;
        this.problemMapper = problemMapper;
        this.userInfoMapper = userInfoMapper;
        this.permissionService = permissionService;
        this.userRoleService = userRoleService;
        this.judgeClient = judgeClient;
        this.codeStorage = codeStorage;
        this.transactionTemplate = transactionTemplate;
        this.rankingStrategyFactory = rankingStrategyFactory;
    }

    // ───────────────────────── CRUD ─────────────────────────

    public ContestCreateVO createContest(ContestCreateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertCanCreate(userId, ResourceType.CONTEST);

        validateRuleType(dto.getRuleType());
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BadRequestException("end_time 必须晚于 start_time");
        }

        Contest contest = new Contest();
        contest.setTitle(normalizeRequiredText(dto.getTitle(), "title 不能为空"));
        contest.setSubtitle(dto.getSubtitle());
        contest.setDescription(dto.getDescription());
        contest.setStartTime(dto.getStartTime());
        contest.setEndTime(dto.getEndTime());
        contest.setRuleType(dto.getRuleType());
        contest.setIsPublic(Boolean.TRUE.equals(dto.getIsPublic()));
        contest.setHideStatistics(Boolean.TRUE.equals(dto.getHideStatistics()));
        contest.setScoringConfig(dto.getScoringConfig());
        contest.setScoringScript(dto.getScoringScript());
        contest.setCreatedByUserId(userId);
        contestMapper.insert(contest);

        permissionService.autoGrantCreator(ResourceType.CONTEST, contest.getId(), userId);

        ContestCreateVO vo = new ContestCreateVO();
        vo.setContestId(contest.getId());
        return vo;
    }

    public ContestPageVO getContestPage(Integer current, Integer size,
                                        String status, String titleKeyword,
                                        String ruleType,
                                        LocalDateTime startTimeFrom, LocalDateTime endTimeTo) {
        if (current == null || current < 1) {
            throw new BadRequestException("页码必须大于等于 1");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BadRequestException("每页条数必须在 1 到 100 之间");
        }

        Long userId = AuthContexts.userIdOrNull();
        boolean isAdmin = userId != null && userRoleService.isAdmin(userId);

        Page<ContestPageItemVO> page = new Page<>(current, size);
        IPage<ContestPageItemVO> pageResult = contestMapper.selectContestPage(
                page, userId, isAdmin, status, titleKeyword, ruleType, startTimeFrom, endTimeTo);

        ContestPageVO vo = new ContestPageVO();
        vo.setCurrent(pageResult.getCurrent());
        vo.setSize(pageResult.getSize());
        vo.setTotal(pageResult.getTotal());
        vo.setRecords(pageResult.getRecords() == null ? Collections.emptyList() : pageResult.getRecords());
        return vo;
    }

    public ContestDetailVO getContestDetail(Long contestId) {
        Contest contest = loadContestOrThrow(contestId);
        Long userId = AuthContexts.userIdOrNull();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ);

        ContestStatus contestStatus = computeStatus(contest);
        boolean isAdmin = userId != null && userRoleService.isAdmin(userId);

        List<ContestProblemOverviewVO> problemList;
        if (contestStatus == ContestStatus.NOT_STARTED && !isAdmin
                && !permissionService.hasPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.WRITE)) {
            problemList = Collections.emptyList();
        } else {
            problemList = contestMapper.selectContestProblems(contest.getId());
            if (problemList == null) problemList = Collections.emptyList();
        }

        boolean isRegistered = false;
        if (userId != null) {
            isRegistered = contestRegisterRelMapper.selectCount(
                    new LambdaQueryWrapper<ContestRegisterRel>()
                            .eq(ContestRegisterRel::getContestId, contest.getId())
                            .eq(ContestRegisterRel::getUserId, userId)) > 0;
        }

        ContestDetailVO vo = new ContestDetailVO();
        vo.setTitle(contest.getTitle());
        vo.setSubtitle(contest.getSubtitle());
        vo.setDescription(contest.getDescription());
        vo.setStartTime(contest.getStartTime());
        vo.setEndTime(contest.getEndTime());
        vo.setStatus(contestStatus.name());
        vo.setIsRegistered(isRegistered);
        vo.setRuleType(contest.getRuleType());
        vo.setIsPublic(contest.getIsPublic());
        vo.setHideStatistics(contest.getHideStatistics());
        vo.setProblemList(problemList);
        return vo;
    }

    public void updateContest(Long contestId, ContestUpdateDTO dto) {
        Contest contest = loadContestOrThrow(contestId);
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.WRITE);

        Contest update = new Contest();
        update.setId(contest.getId());
        boolean changed = false;

        if (dto.getTitle() != null) {
            update.setTitle(normalizeRequiredText(dto.getTitle(), "title 不能为空"));
            changed = true;
        }
        if (dto.getSubtitle() != null) { update.setSubtitle(dto.getSubtitle()); changed = true; }
        if (dto.getDescription() != null) { update.setDescription(dto.getDescription()); changed = true; }
        if (dto.getStartTime() != null) { update.setStartTime(dto.getStartTime()); changed = true; }
        if (dto.getEndTime() != null) { update.setEndTime(dto.getEndTime()); changed = true; }
        if (dto.getRuleType() != null) {
            validateRuleType(dto.getRuleType());
            update.setRuleType(dto.getRuleType());
            changed = true;
        }
        if (dto.getIsPublic() != null) { update.setIsPublic(dto.getIsPublic()); changed = true; }
        if (dto.getHideStatistics() != null) { update.setHideStatistics(dto.getHideStatistics()); changed = true; }
        if (dto.getScoringConfig() != null) { update.setScoringConfig(dto.getScoringConfig()); changed = true; }
        if (dto.getScoringScript() != null) { update.setScoringScript(dto.getScoringScript()); changed = true; }

        if (changed) {
            contestMapper.updateById(update);
        }
    }

    // ───────────────────────── Problem management ─────────────────────────

    public void replaceContestProblems(Long contestId, ContestProblemEditDTO dto) {
        Contest contest = loadContestOrThrow(contestId);
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.WRITE);

        List<ProblemPlanItem> planItems = buildProblemPlan(dto.getProblemList());
        Map<String, Problem> problemByPid = loadProblemMapByPid(planItems);

        Map<Long, Integer> desiredOrderByProblemId = new HashMap<>();
        for (ProblemPlanItem item : planItems) {
            Problem problem = problemByPid.get(item.pid);
            desiredOrderByProblemId.put(problem.getId(), item.sortOrder);
        }

        List<ContestProblemRel> existingRels = contestProblemRelMapper.selectByContestId(contest.getId());
        Map<Long, ContestProblemRel> activeByProblemId = new HashMap<>();
        Map<Long, ContestProblemRel> deletedByProblemId = new HashMap<>();
        for (ContestProblemRel rel : existingRels) {
            if (rel == null || rel.getProblemId() == null) continue;
            Long problemId = rel.getProblemId();
            if (Integer.valueOf(0).equals(rel.getIsDel())) {
                activeByProblemId.putIfAbsent(problemId, rel);
            } else if (!activeByProblemId.containsKey(problemId)) {
                deletedByProblemId.putIfAbsent(problemId, rel);
            }
        }

        Set<Long> desiredProblemIds = desiredOrderByProblemId.keySet();
        Set<Long> toSoftDelete = new LinkedHashSet<>();
        for (Long activeProblemId : activeByProblemId.keySet()) {
            if (!desiredProblemIds.contains(activeProblemId)) {
                toSoftDelete.add(activeProblemId);
            }
        }
        Set<Long> toKeep = new LinkedHashSet<>();
        Set<Long> toRestore = new LinkedHashSet<>();
        Set<Long> toInsert = new LinkedHashSet<>();
        for (Long desiredProblemId : desiredProblemIds) {
            if (activeByProblemId.containsKey(desiredProblemId)) {
                toKeep.add(desiredProblemId);
            } else if (deletedByProblemId.containsKey(desiredProblemId)) {
                toRestore.add(desiredProblemId);
            } else {
                toInsert.add(desiredProblemId);
            }
        }

        if (!toSoftDelete.isEmpty()) {
            List<Long> softDeleteIds = toSoftDelete.stream()
                    .map(activeByProblemId::get)
                    .filter(rel -> rel != null && rel.getId() != null)
                    .map(ContestProblemRel::getId)
                    .toList();
            if (!softDeleteIds.isEmpty()) {
                contestProblemRelMapper.markDeletedByIds(contest.getId(), softDeleteIds);
            }
        }

        List<ContestProblemRel> keepNeedReorder = new ArrayList<>();
        for (Long problemId : toKeep) {
            ContestProblemRel rel = activeByProblemId.get(problemId);
            Integer desiredOrder = desiredOrderByProblemId.get(problemId);
            if (rel == null || rel.getId() == null || desiredOrder == null) continue;
            if (!desiredOrder.equals(rel.getSortOrder())) {
                keepNeedReorder.add(new ContestProblemRel()
                        .setId(rel.getId())
                        .setSortOrder(desiredOrder));
            }
        }

        if (!keepNeedReorder.isEmpty()) {
            int temporarySortBase = calculateTemporarySortBase(existingRels, desiredOrderByProblemId.values(), keepNeedReorder.size());
            List<ContestProblemRel> temporaryUpdates = new ArrayList<>(keepNeedReorder.size());
            int offset = 0;
            for (ContestProblemRel rel : keepNeedReorder) {
                temporaryUpdates.add(new ContestProblemRel()
                        .setId(rel.getId())
                        .setSortOrder(temporarySortBase + offset));
                offset++;
            }
            contestProblemRelMapper.updateSortOrdersByIds(contest.getId(), temporaryUpdates);
        }

        if (!toRestore.isEmpty()) {
            List<ContestProblemRel> restoreUpdates = new ArrayList<>(toRestore.size());
            for (Long problemId : toRestore) {
                ContestProblemRel rel = deletedByProblemId.get(problemId);
                Integer desiredOrder = desiredOrderByProblemId.get(problemId);
                if (rel == null || rel.getId() == null || desiredOrder == null) continue;
                restoreUpdates.add(new ContestProblemRel()
                        .setId(rel.getId())
                        .setSortOrder(desiredOrder));
            }
            if (!restoreUpdates.isEmpty()) {
                contestProblemRelMapper.restoreByIdsWithSortOrders(contest.getId(), restoreUpdates);
            }
        }

        if (!toInsert.isEmpty()) {
            List<ContestProblemRel> insertRels = new ArrayList<>(toInsert.size());
            for (Long problemId : toInsert) {
                Integer desiredOrder = desiredOrderByProblemId.get(problemId);
                if (desiredOrder == null) continue;
                insertRels.add(new ContestProblemRel()
                        .setContestId(contest.getId())
                        .setProblemId(problemId)
                        .setSortOrder(desiredOrder));
            }
            if (!insertRels.isEmpty()) {
                contestProblemRelMapper.insertBatch(insertRels);
            }
        }

        if (!keepNeedReorder.isEmpty()) {
            contestProblemRelMapper.updateSortOrdersByIds(contest.getId(), keepNeedReorder);
        }
    }

    public ContestProblemListInEditVO getContestProblemListForEdit(Long contestId) {
        Contest contest = loadContestOrThrow(contestId);
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.WRITE);

        List<ContestProblemOverviewVO> problems = contestMapper.selectContestProblems(contest.getId());
        ContestProblemListInEditVO vo = new ContestProblemListInEditVO();
        vo.setProblemList(problems == null ? Collections.emptyList() : problems);
        return vo;
    }

    // ───────────────────────── Registration ─────────────────────────

    public void registerContest(Long contestId) {
        Contest contest = loadContestOrThrow(contestId);
        Long userId = AuthContexts.requiredUserId();

        ContestStatus status = computeStatus(contest);
        if (status == ContestStatus.FINISHED) {
            throw new BadRequestException("比赛已结束，无法报名");
        }

        long existingCount = contestRegisterRelMapper.selectCount(
                new LambdaQueryWrapper<ContestRegisterRel>()
                        .eq(ContestRegisterRel::getContestId, contest.getId())
                        .eq(ContestRegisterRel::getUserId, userId));
        if (existingCount > 0) {
            throw new BadRequestException("已报名该比赛");
        }

        ContestRegisterRel reg = new ContestRegisterRel();
        reg.setContestId(contest.getId());
        reg.setUserId(userId);
        reg.setJoinedAt(LocalDateTime.now());
        contestRegisterRelMapper.insert(reg);
    }

    public void unregisterContest(Long contestId) {
        Contest contest = loadContestOrThrow(contestId);
        Long userId = AuthContexts.requiredUserId();

        ContestStatus status = computeStatus(contest);
        if (status != ContestStatus.NOT_STARTED) {
            throw new BadRequestException("比赛已开始，无法取消报名");
        }

        ContestRegisterRel reg = contestRegisterRelMapper.selectOne(
                new LambdaQueryWrapper<ContestRegisterRel>()
                        .eq(ContestRegisterRel::getContestId, contest.getId())
                        .eq(ContestRegisterRel::getUserId, userId));
        if (reg == null) {
            throw new BadRequestException("尚未报名该比赛");
        }

        contestRegisterRelMapper.deleteById(reg.getId());
    }

    // ───────────────────────── Submission ─────────────────────────

    public ContestSubmitVO submitContestSolution(Long contestId, ContestSubmitDTO dto) {
        Contest contest = loadContestOrThrow(contestId);
        Long userId = AuthContexts.requiredUserId();

        ContestStatus status = computeStatus(contest);
        if (status != ContestStatus.IN_PROGRESS) {
            throw new BadRequestException("只能在比赛进行中提交");
        }

        long regCount = contestRegisterRelMapper.selectCount(
                new LambdaQueryWrapper<ContestRegisterRel>()
                        .eq(ContestRegisterRel::getContestId, contest.getId())
                        .eq(ContestRegisterRel::getUserId, userId));
        if (regCount == 0) {
            throw new BadRequestException("请先报名比赛");
        }

        List<ContestProblemOverviewVO> contestProblems = contestMapper.selectContestProblems(contest.getId());
        ContestProblemOverviewVO matchedProblem = null;
        for (ContestProblemOverviewVO p : contestProblems) {
            if (dto.getPid().equals(p.getPid())) {
                matchedProblem = p;
                break;
            }
        }
        if (matchedProblem == null) {
            throw new BadRequestException("题目不在本比赛中: " + dto.getPid());
        }

        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, dto.getPid()));
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

        Submission submission = submitContestInTransaction(dto, userId, problem.getId(), contest.getId());
        if (submission == null) {
            throw new InternalServerException("提交失败");
        }

        sendToJudgeServer(submission.getSubmissionNo(), dto.getPid(), dto.getCode(), dto.getLanguage());

        ContestSubmitVO vo = new ContestSubmitVO();
        vo.setSubmissionNo(submission.getSubmissionNo());
        return vo;
    }

    private Submission submitContestInTransaction(ContestSubmitDTO dto, Long userId, Long problemId, Long contestId) {
        return transactionTemplate.execute(txStatus -> {
            Submission submission = new Submission();
            submission.setSubmissionNo(UUID.randomUUID().toString());
            submission.setUserId(userId);
            submission.setProblemId(problemId);
            submission.setLanguage(dto.getLanguage());
            submission.setStatus(SubmissionStatus.PENDING.getStatus());
            submission.setSubmitTime(LocalDateTime.now());
            submissionMapper.insert(submission);

            ContestSubmission cs = new ContestSubmission();
            cs.setContestId(contestId);
            cs.setSubmissionId(submission.getId());
            cs.setCreatedAt(LocalDateTime.now());
            contestSubmissionMapper.insert(cs);

            codeStorage.save(dto.getCode(), submission.getSubmissionNo());

            String submissionNo = submission.getSubmissionNo();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
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
            log.error("评测端提交失败, submissionNo={}", submissionNo, e);
            submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                    .set(Submission::getStatus, SubmissionStatus.FAILED.getStatus())
                    .eq(Submission::getSubmissionNo, submissionNo)
                    .eq(Submission::getStatus, SubmissionStatus.PENDING.getStatus()));
        }
    }

    public ContestSubmissionPageVO getContestSubmissionPage(Long contestId, Integer current, Integer size) {
        Contest contest = loadContestOrThrow(contestId);
        Long userId = AuthContexts.userIdOrNull();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ);

        if (current == null || current < 1) {
            throw new BadRequestException("页码必须大于等于 1");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BadRequestException("每页条数必须在 1 到 100 之间");
        }

        Page<ContestSubmissionRecordVO> page = new Page<>(current, size);
        IPage<ContestSubmissionRecordVO> pageResult = contestMapper.selectContestSubmissionPage(page, contest.getId());

        ContestSubmissionPageVO vo = new ContestSubmissionPageVO();
        vo.setCurrent(pageResult.getCurrent());
        vo.setSize(pageResult.getSize());
        vo.setTotal(pageResult.getTotal());
        vo.setRecords(pageResult.getRecords() == null ? Collections.emptyList() : pageResult.getRecords());
        return vo;
    }

    public ContestSubmissionDetailVO getContestSubmissionDetail(Long contestId, String submissionNo) {
        Contest contest = loadContestOrThrow(contestId);
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ);

        Submission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<Submission>().eq(Submission::getSubmissionNo, submissionNo));
        if (submission == null) {
            throw new NotFoundException("提交记录不存在");
        }

        long csCount = contestSubmissionMapper.selectCount(
                new LambdaQueryWrapper<ContestSubmission>()
                        .eq(ContestSubmission::getContestId, contest.getId())
                        .eq(ContestSubmission::getSubmissionId, submission.getId()));
        if (csCount == 0) {
            throw new NotFoundException("提交不属于本比赛");
        }

        boolean isOwner = userId.equals(submission.getUserId());
        boolean isAdmin = userRoleService.isAdmin(userId);
        boolean hasWrite = permissionService.hasPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.WRITE);
        if (!isOwner && !isAdmin && !hasWrite) {
            throw new ForbiddenException("无权查看他人提交记录");
        }

        Problem problem = problemMapper.selectById(submission.getProblemId());
        SubmissionDetail detail = submissionDetailMapper.selectById(submission.getId());
        UserInfo user = userInfoMapper.selectById(submission.getUserId());

        ContestSubmissionDetailVO vo = new ContestSubmissionDetailVO();
        vo.setSubmissionNo(submission.getSubmissionNo());
        vo.setLanguage(submission.getLanguage());
        vo.setStatus(submission.getStatus());
        vo.setVerdict(submission.getVerdict());
        vo.setScore(submission.getScore());
        vo.setSubmitTime(submission.getSubmitTime());
        vo.setCode(codeStorage.getCode(submission.getSubmissionNo()));
        vo.setUsername(user != null ? user.getUsername() : null);
        if (detail != null) {
            vo.setResultDetail(detail.getResultDetail());
            vo.setErrorDetail(detail.getErrorDetail());
        }
        if (problem != null) {
            ContestProblemOverviewVO pvo = new ContestProblemOverviewVO();
            pvo.setPid(problem.getPid());
            pvo.setTitle(problem.getTitle());
            vo.setProblem(pvo);
        }
        return vo;
    }

    // ───────────────────────── Standings ─────────────────────────

    public ContestStandingsVO getContestStandings(Long contestId) {
        Contest contest = loadContestOrThrow(contestId);
        Long userId = AuthContexts.userIdOrNull();
        permissionService.assertPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.READ);

        List<ContestProblemOverviewVO> problems = contestMapper.selectContestProblems(contest.getId());
        if (problems == null) problems = Collections.emptyList();

        List<UserInfo> registeredUserList = contestMapper.selectContestRegisteredUsers(contest.getId());
        Map<Long, UserInfo> registeredUsers = new LinkedHashMap<>();
        for (UserInfo u : registeredUserList) {
            registeredUsers.put(u.getId(), u);
        }

        List<Submission> finishedSubmissions = contestMapper.selectContestFinishedSubmissions(contest.getId());
        if (finishedSubmissions == null) finishedSubmissions = Collections.emptyList();

        Map<Long, String> problemIdToPid = buildProblemIdToPidMap(problems);

        List<ContestStandingsRecordVO> records = rankingStrategyFactory
                .getStrategy(contest.getRuleType())
                .computeStandings(problems, registeredUsers, finishedSubmissions, contest, problemIdToPid);

        boolean isAdmin = userId != null && userRoleService.isAdmin(userId);
        boolean hasWrite = userId != null && permissionService.hasPermission(userId, ResourceType.CONTEST, contest.getId(), PermissionOp.WRITE);
        if (Boolean.TRUE.equals(contest.getHideStatistics())
                && computeStatus(contest) == ContestStatus.IN_PROGRESS
                && !isAdmin && !hasWrite) {
            for (ContestStandingsRecordVO r : records) {
                r.setScoreDetails(Collections.emptyMap());
            }
        }

        ContestStandingsVO vo = new ContestStandingsVO();
        vo.setContestId(contest.getId());
        vo.setRuleType(contest.getRuleType());
        vo.setProblems(problems);
        vo.setRecords(records);
        return vo;
    }

    // ───────────────────────── Helpers ─────────────────────────

    private Contest loadContestOrThrow(Long contestId) {
        if (contestId == null) {
            throw new BadRequestException("contest_id 不能为空");
        }
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new NotFoundException("比赛不存在");
        }
        return contest;
    }

    private ContestStatus computeStatus(Contest contest) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime())) return ContestStatus.NOT_STARTED;
        if (!now.isAfter(contest.getEndTime())) return ContestStatus.IN_PROGRESS;
        return ContestStatus.FINISHED;
    }

    private Map<Long, String> buildProblemIdToPidMap(List<ContestProblemOverviewVO> problems) {
        if (problems == null || problems.isEmpty()) return Collections.emptyMap();

        List<String> pids = problems.stream().map(ContestProblemOverviewVO::getPid).toList();
        List<Problem> problemEntities = problemMapper.selectList(
                new LambdaQueryWrapper<Problem>().in(Problem::getPid, pids));

        Map<Long, String> map = new HashMap<>();
        for (Problem p : problemEntities) {
            map.put(p.getId(), p.getPid());
        }
        return map;
    }

    private void validateRuleType(String ruleType) {
        if (!"ACM".equals(ruleType) && !"NOI".equals(ruleType)
                && !"IOI".equals(ruleType) && !"CUSTOM".equals(ruleType)) {
            throw new BadRequestException("未知赛制: " + ruleType);
        }
    }

    private List<ProblemPlanItem> buildProblemPlan(List<ContestProblemEditDTO.ProblemItemDTO> input) {
        if (input == null) return Collections.emptyList();

        List<ProblemPlanItem> plan = new ArrayList<>(input.size());
        Set<String> pidSet = new LinkedHashSet<>();
        Set<Integer> orderSet = new LinkedHashSet<>();

        for (ContestProblemEditDTO.ProblemItemDTO item : input) {
            if (item == null) {
                throw new BadRequestException("problem_list 中存在空对象");
            }
            String pid = normalizeRequiredText(item.getPid(), "problem_list.pid 不能为空");
            Integer sortOrder = item.getSortOrder();

            if (!pidSet.add(pid)) {
                throw new BadRequestException("problem_list 中 pid 重复: " + pid);
            }
            if (!orderSet.add(sortOrder)) {
                throw new BadRequestException("problem_list 中 sort_order 重复: " + sortOrder);
            }
            plan.add(new ProblemPlanItem(pid, sortOrder));
        }
        return plan;
    }

    private Map<String, Problem> loadProblemMapByPid(List<ProblemPlanItem> planItems) {
        if (planItems.isEmpty()) return Collections.emptyMap();

        List<String> pids = planItems.stream().map(ProblemPlanItem::pid).toList();
        List<Problem> problems = problemMapper.selectList(
                new LambdaQueryWrapper<Problem>().in(Problem::getPid, pids));

        Map<String, Problem> problemByPid = new HashMap<>();
        for (Problem problem : problems) {
            problemByPid.put(problem.getPid(), problem);
        }

        List<String> missingPids = pids.stream()
                .filter(pid -> !problemByPid.containsKey(pid))
                .distinct()
                .toList();
        if (!missingPids.isEmpty()) {
            throw new BadRequestException("以下题目不存在: " + String.join(", ", missingPids));
        }
        return problemByPid;
    }

    private int calculateTemporarySortBase(List<ContestProblemRel> existingRels,
                                           java.util.Collection<Integer> desiredOrders,
                                           int updateCount) {
        int maxOrder = 0;
        for (ContestProblemRel rel : existingRels) {
            if (rel != null && rel.getSortOrder() != null) {
                maxOrder = Math.max(maxOrder, rel.getSortOrder());
            }
        }
        for (Integer desiredOrder : desiredOrders) {
            if (desiredOrder != null) {
                maxOrder = Math.max(maxOrder, desiredOrder);
            }
        }
        long candidate = (long) maxOrder + 1_000_000L;
        int tail = Math.max(8, updateCount + 2);
        long maxAllowed = (long) Integer.MAX_VALUE - tail;
        if (candidate > maxAllowed) candidate = maxAllowed;
        if (candidate <= 0) candidate = 1_000_000_000L;
        return (int) candidate;
    }

    private String normalizeRequiredText(String raw, String message) {
        if (!StringUtils.hasText(raw)) {
            throw new BadRequestException(message);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException(message);
        }
        return trimmed;
    }

    private record ProblemPlanItem(String pid, Integer sortOrder) {
    }
}
