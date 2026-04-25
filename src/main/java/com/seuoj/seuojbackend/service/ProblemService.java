package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.common.ProblemSourceType;
import com.seuoj.seuojbackend.common.PermissionOp;
import com.seuoj.seuojbackend.common.ResourceType;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeProblemEditRequest;
import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import com.seuoj.seuojbackend.model.ProblemCommon;
import com.seuoj.seuojbackend.dto.problem.ProblemCreateDTO;
import com.seuoj.seuojbackend.dto.problem.ProblemDetailQuery;
import com.seuoj.seuojbackend.dto.problem.ProblemEditDTO;
import com.seuoj.seuojbackend.entity.Assignment;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.ProblemTagRel;
import com.seuoj.seuojbackend.entity.Tag;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.AssignmentMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.ProblemTagRelMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.mapper.TagMapper;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.vo.problem.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ProblemService {

    private final ProblemMapper problemMapper;
    private final JudgeClient judgeClient;
    private final TagMapper tagMapper;
    private final ProblemTagRelMapper problemTagRelMapper;
    private final PermissionService permissionService;
    private final UserRoleService userRoleService;
    private final ProblemPidGenerator pidGenerator;
    private final AssignmentMapper assignmentMapper;
    private final SubmissionMapper submissionMapper;
    private final ImageService imageService;

    public ProblemService(ProblemMapper problemMapper, JudgeClient judgeClient, TagMapper tagMapper,
                          ProblemTagRelMapper problemTagRelMapper,
                          PermissionService permissionService, UserRoleService userRoleService,
                          ProblemPidGenerator pidGenerator,
                          AssignmentMapper assignmentMapper,
                          SubmissionMapper submissionMapper) {
                          AssignmentMapper assignmentMapper,
                          ImageService imageService) {
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
        this.tagMapper = tagMapper;
        this.problemTagRelMapper = problemTagRelMapper;
        this.permissionService = permissionService;
        this.userRoleService = userRoleService;
        this.pidGenerator = pidGenerator;
        this.assignmentMapper = assignmentMapper;
        this.submissionMapper = submissionMapper;
        this.imageService = imageService;
    }

    public ProblemPageVO getProblemPage(Integer current, Integer size, String title, List<Long> tagIds) {
        if (tagIds != null && !tagIds.isEmpty()) {
            tagIds = tagIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }
        int tagIdsSize = (tagIds == null) ? 0 : tagIds.size();

        SearchSpec searchSpec = SearchSpec.from(title);

        Page<?> page = new Page<>(current, size);
        IPage<ProblemListItemVO> resultPage = problemMapper.selectProblemPage(
                page,
                searchSpec.fulltextQuery,
                searchSpec.titleLike,
                searchSpec.useFulltext,
                searchSpec.singleTokens,
                searchSpec.useLikeSingle,
                searchSpec.useLikeRaw,
                canCurrentUserViewPrivateProblems(),
                tagIds,
                tagIdsSize
        );

        List<ProblemListItemVO> records = resultPage.getRecords();
        if (records != null && !records.isEmpty()) {
            List<String> pids = records.stream()
                    .map(ProblemListItemVO::getPid)
                    .collect(Collectors.toList());

            List<ProblemMapper.ProblemTagResult> tagResults = problemMapper.getProblemTagsBatch(pids);

            Map<String, List<String>> pidTagsMap = new HashMap<>();
            for (ProblemMapper.ProblemTagResult tagResult : tagResults) {
                pidTagsMap.computeIfAbsent(tagResult.getPid(), k -> new ArrayList<>())
                        .add(tagResult.getTagName());
            }

            for (ProblemListItemVO item : records) {
                item.setTags(pidTagsMap.getOrDefault(item.getPid(), Collections.emptyList()));
            }
        }

        ProblemPageVO vo = new ProblemPageVO();
        vo.setCurrent(current);
        vo.setSize(size);
        vo.setTotal(resultPage.getTotal());
        vo.setRecords(records != null ? records : Collections.emptyList());

        return vo;
    }

    private boolean canCurrentUserViewPrivateProblems() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.isGuest()) {
            return false;
        }
        return userRoleService.isTeacherOrAdmin(ctx.getUserId());
    }

    private static String buildFulltextQuery(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(token).append('*');
        }
        return builder.toString();
    }

    private record SearchSpec(String fulltextQuery, String titleLike, List<String> singleTokens, boolean useFulltext,
                              boolean useLikeSingle, boolean useLikeRaw) {

        private static SearchSpec from(String title) {
            List<String> tokens = tokenizeForFulltext(title);
            List<String> singleTokens = tokens.stream()
                    .filter(token -> token.length() == 1)
                    .collect(Collectors.toList());
            List<String> multiTokens = tokens.stream()
                    .filter(token -> token.length() >= 2)
                    .collect(Collectors.toList());

            String fulltextQuery = buildFulltextQuery(multiTokens);
            boolean useFulltext = !fulltextQuery.isEmpty();
            boolean useLikeSingle = !singleTokens.isEmpty();
            boolean useLikeRaw = !useFulltext && !useLikeSingle && title != null && !title.trim().isEmpty();

            return new SearchSpec(fulltextQuery, title, singleTokens, useFulltext, useLikeSingle, useLikeRaw);
        }
    }

    private static List<String> tokenizeForFulltext(String input) {
        if (input == null) {
            return Collections.emptyList();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int mode = 0;

        int index = 0;
        while (index < trimmed.length()) {
            int codePoint = trimmed.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                if (mode == 2) {
                    addAlnumToken(tokens, buffer);
                    buffer.setLength(0);
                }
                mode = 1;
                buffer.appendCodePoint(codePoint);
            } else if (Character.isLetterOrDigit(codePoint)) {
                if (mode == 1) {
                    addCjkBigrams(tokens, buffer);
                    buffer.setLength(0);
                }
                mode = 2;
                buffer.appendCodePoint(codePoint);
            } else {
                if (mode == 1) {
                    addCjkBigrams(tokens, buffer);
                } else if (mode == 2) {
                    addAlnumToken(tokens, buffer);
                }
                buffer.setLength(0);
                mode = 0;
            }
            index += charCount;
        }

        if (mode == 1) {
            addCjkBigrams(tokens, buffer);
        } else if (mode == 2) {
            addAlnumToken(tokens, buffer);
        }

        return tokens.stream().distinct().collect(Collectors.toList());
    }

    private static void addAlnumToken(List<String> tokens, StringBuilder buffer) {
        if (!buffer.isEmpty()) {
            tokens.add(buffer.toString());
        }
    }

    private static void addCjkBigrams(List<String> tokens, StringBuilder buffer) {
        if (buffer.isEmpty()) {
            return;
        }
        int[] codePoints = buffer.codePoints().toArray();
        if (codePoints.length == 1) {
            tokens.add(new String(codePoints, 0, 1));
            return;
        }
        for (int i = 0; i < codePoints.length - 1; i++) {
            tokens.add(new String(codePoints, i, 2));
        }
    }

    public ProblemDetailVO getProblemDetail(ProblemDetailQuery query) {
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, query.pid()));
        if (problem == null) {
            log.warn("获取题目详情时题目不存在, pid={}", query.pid());
            throw new NotFoundException("题目不存在");
        }

        Long userId = AuthContexts.userIdOrNull();
        switch (query.sourceType()) {
            case DIRECT -> permissionService.assertPermission(userId, ResourceType.PROBLEM, problem.getId(), PermissionOp.READ);
            case CONTEST -> permissionService.assertProblemAccessViaContest(userId, problem.getId(), query.ownerId());
            case PROBLEM_SET -> permissionService.assertProblemAccessViaProblemSet(userId, problem.getId(), query.ownerId());
            case ASSIGNMENT -> permissionService.assertProblemAccessViaAssignment(userId, problem.getId(), query.ownerId());
        }

        ProblemDetailVO problemDetail = problemMapper.getProblemDetail(query.pid());
        if (problemDetail == null) {
            log.warn("获取题目详情时题目详情不存在, pid={}", query.pid());
            throw new NotFoundException("题目不存在");
        }

        List<String> tags = problemMapper.getProblemTags(query.pid());
        problemDetail.setTags(tags != null ? tags : Collections.emptyList());

        ProblemContentDTO problemContentDTO = judgeClient.fetchProblemContent(query.pid());
        if (problemContentDTO == null) {
            log.warn("题目 {} 的内容在评测服务中缺失", query.pid());
            throw new NotFoundException("题目不存在");
        }

        problemDetail.setContent(problemContentDTO);

        ProblemConfigDTO problemConfigDTO = judgeClient.fetchProblemConfig(query.pid());
        fillProblemContentByProblemConfig(problemContentDTO, problemConfigDTO);

        if (query.sourceType() == ProblemSourceType.ASSIGNMENT) {
            Assignment assignment = assignmentMapper.selectById(query.ownerId());
            boolean open = assignment != null
                    && (assignment.getVisibleTo() == null || !LocalDateTime.now().isAfter(assignment.getVisibleTo()));
            problemDetail.setSubmittable(open);
        } else {
            problemDetail.setSubmittable(true);
        }

        return problemDetail;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProblemCreateVO createProblem(ProblemCreateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        permissionService.assertCanCreate(userId, ResourceType.PROBLEM);

        Problem problem = new Problem();
        problem.setPid(dto.getPid());
        problem.setTitle(dto.getTitle().trim());
        problem.setIsPublic(dto.getIsPublic());
        problem.setTotalSubmit(0);
        problem.setTotalAccept(0);
        problem.setCreatedByUserId(userId);

        try {
            problemMapper.insert(problem);
        } catch (DuplicateKeyException e) {
            log.warn("创建题目时 pid 冲突, pid={}", dto.getPid());
            throw new BadRequestException("pid 已存在");
        }

        permissionService.autoGrantCreator(ResourceType.PROBLEM, problem.getId(), userId);

        if (dto.getTags() != null) {
            updateProblemTags(problem.getId(), dto.getTags());
        }
        judgeClient.updateProblem(buildJudgeRequest(
                dto.getPid(),
                dto.getDescription(),
                dto.getInput(),
                dto.getOutput(),
                dto.getExample(),
                dto.getHint()
        ));

        ProblemCreateVO vo = new ProblemCreateVO();
        vo.setPid(dto.getPid());
        return vo;
    }

    public NextProblemIdVO getNextProblemId() {
        Integer nextProblemId = problemMapper.selectNextAvailableNumericPid();
        int availableId = nextProblemId != null ? nextProblemId : 1;

        NextProblemIdVO vo = new NextProblemIdVO();
        vo.setNextPid(pidGenerator.generate((long) availableId));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void editProblem(ProblemEditDTO dto) {
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, dto.getPid()));
        if (problem == null) {
            log.warn("编辑题面时题目不存在, pid={}", dto.getPid());
            throw new NotFoundException("题目不存在");
        }

        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.PROBLEM, problem.getId(), PermissionOp.WRITE);

        updateProblemMeta(problem.getId(), dto.getTitle(), dto.getIsPublic());
        if (dto.getTags() != null) {
            updateProblemTags(problem.getId(), dto.getTags());
        }
        judgeClient.updateProblem(buildJudgeRequest(dto));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteProblem(String pid) {
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("删除题目时题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        Long userId = AuthContexts.requiredUserId();
        permissionService.assertPermission(userId, ResourceType.PROBLEM, problem.getId(), PermissionOp.WRITE);

        validateProblemDeleteRules(problem.getId(), pid);

        problemTagRelMapper.markAllDeletedByProblemId(problem.getId());
        problemMapper.deleteById(problem.getId());
        imageService.unbindResource(ResourceType.PROBLEM, problem.getId());

        judgeClient.deleteProblem(pid);
    }

    private void validateProblemDeleteRules(Long problemId, String pid) {
        List<String> reasons = new ArrayList<>();

        long activeSubmissionCount = problemMapper.countActiveSubmissionsByProblemId(problemId);
        if (activeSubmissionCount > 0) {
            reasons.add("存在提交记录（" + activeSubmissionCount + "）");
        }

        long activeContestRelationCount = problemMapper.countActiveContestRelationsByProblemId(problemId);
        if (activeContestRelationCount > 0) {
            reasons.add("仍在比赛中被引用（" + activeContestRelationCount + "）");
        }

        long activeContestSubmissionCount = problemMapper.countActiveContestSubmissionsByProblemId(problemId);
        if (activeContestSubmissionCount > 0) {
            reasons.add("存在比赛提交关联记录（" + activeContestSubmissionCount + "）");
        }

        long activeProblemSetRelationCount = problemMapper.countActiveProblemSetRelationsByProblemId(problemId);
        if (activeProblemSetRelationCount > 0) {
            reasons.add("仍在题单中被引用（" + activeProblemSetRelationCount + "）");
        }

        if (!reasons.isEmpty()) {
            throw new ConflictException("题目 " + pid + " 删除校验未通过，请先清理关联数据：" + String.join("；", reasons));
        }
    }

    private void updateProblemMeta(Long problemId, String title, Boolean isPublic) {
        boolean needUpdate = false;

        Problem problem = new Problem();
        problem.setId(problemId);

        if (StringUtils.hasText(title)) {
            problem.setTitle(title.trim());
            needUpdate = true;
        }
        if (isPublic != null) {
            problem.setIsPublic(isPublic);
            needUpdate = true;
        }
        if (needUpdate) {
            problemMapper.updateById(problem);
        }
    }

    private void updateProblemTags(Long problemId, List<Long> tags) {
        Set<Long> tagIds = tags.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (tagIds.isEmpty()) {
            problemTagRelMapper.markAllDeletedByProblemId(problemId);
            return;
        }

        List<Tag> existingTags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .in(Tag::getId, tagIds));
        if (existingTags.size() != tagIds.size()) {
            log.warn("更新题目标签时存在无效标签, problemId={}, tagIds={}", problemId, tagIds);
            throw new BadRequestException("标签不存在");
        }

        problemTagRelMapper.markAllDeletedByProblemId(problemId);

        List<Long> existingRelTagIds = problemTagRelMapper
                .selectTagIdsByProblemIdAndTagIds(problemId, tagIds);
        Set<Long> existingRelTagIdSet = new LinkedHashSet<>(existingRelTagIds);
        if (!existingRelTagIdSet.isEmpty()) {
            problemTagRelMapper.restoreByProblemIdAndTagIds(problemId, existingRelTagIdSet);
        }

        List<ProblemTagRel> rels = new ArrayList<>(tagIds.size());
        for (Tag tag : existingTags) {
            if (existingRelTagIdSet.contains(tag.getId())) {
                continue;
            }
            rels.add(new ProblemTagRel()
                    .setProblemId(problemId)
                    .setTagId(tag.getId()));
        }
        if (!rels.isEmpty()) {
            problemTagRelMapper.insertBatch(rels);
        }
    }

    private JudgeProblemEditRequest buildJudgeRequest(ProblemEditDTO dto) {
        return buildJudgeRequest(
                dto.getPid(),
                dto.getDescription(),
                dto.getInput(),
                dto.getOutput(),
                dto.getExample(),
                dto.getHint()
        );
    }

    private JudgeProblemEditRequest buildJudgeRequest(String pid, String description, String input, String output,
                                                      List<ProblemCommon.Example> example, String hint) {
        JudgeProblemEditRequest request = new JudgeProblemEditRequest();
        request.setPid(pid);
        request.setDescription(description);
        request.setInput(input);
        request.setOutput(output);

        if (example != null) {
            List<ProblemCommon.Example> examples = example.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            request.setExample(examples);
        }

        request.setHint(hint);
        return request;
    }

    private void fillProblemContentByProblemConfig(ProblemContentDTO problemContent, ProblemConfigDTO problemConfig) {
        if (problemConfig == null) {
            return;
        }

        long defaultTimeLimitMs = 1000L;
        long defaultMemoryLimitKb = 256L * 1024;
        String problemType = "Standard";
        String checkerType = "Standard";

        if (problemConfig.getProblemInfo() != null) {
            if (problemConfig.getProblemInfo().getTimeLimitMs() != null) {
                defaultTimeLimitMs = problemConfig.getProblemInfo().getTimeLimitMs();
            }
            if (problemConfig.getProblemInfo().getMemoryLimitKb() != null) {
                defaultMemoryLimitKb = problemConfig.getProblemInfo().getMemoryLimitKb();
            }
            if (problemConfig.getProblemInfo().getProblemType() != null) {
                problemType = problemConfig.getProblemInfo().getProblemType();
            }
            if (problemConfig.getProblemInfo().getCheckerType() != null) {
                checkerType = problemConfig.getProblemInfo().getCheckerType();
            }
        }

        long minTimeLimitMs = defaultTimeLimitMs;
        long maxTimeLimitMs = defaultTimeLimitMs;
        long minMemoryLimitKb = defaultMemoryLimitKb;
        long maxMemoryLimitKb = defaultMemoryLimitKb;

        if (problemConfig.getTestcases() != null) {
            for (ProblemConfigDTO.Testcase testcase : problemConfig.getTestcases()) {
                if (testcase == null) {
                    continue;
                }

                if (testcase.getTimeLimitMs() != null) {
                    minTimeLimitMs = Math.min(minTimeLimitMs, testcase.getTimeLimitMs());
                    maxTimeLimitMs = Math.max(maxTimeLimitMs, testcase.getTimeLimitMs());
                }

                if (testcase.getMemoryLimitKb() != null) {
                    minMemoryLimitKb = Math.min(minMemoryLimitKb, testcase.getMemoryLimitKb());
                    maxMemoryLimitKb = Math.max(maxMemoryLimitKb, testcase.getMemoryLimitKb());
                }
            }
        }

        ProblemCommon.ContentInfo info = problemContent.getInfo();
        if (info == null) {
            info = new ProblemCommon.ContentInfo();
            problemContent.setInfo(info);
        }
        info.setMinCpuTimeMs(minTimeLimitMs);
        info.setMaxCpuTimeMs(maxTimeLimitMs);
        info.setMinMemoryKb(minMemoryLimitKb);
        info.setMaxMemoryKb(maxMemoryLimitKb);

        info.setProblemType(problemType);
        info.setCheckerType(checkerType);
        info.setTestCaseNumber(problemConfig.getTestcases() != null ? problemConfig.getTestcases().size() : 0);
    }

    public ProblemStatisticsVO getProblemStatistics(String pid) {
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            throw new NotFoundException("题目不存在");
        }

        ProblemStatisticsVO vo = new ProblemStatisticsVO();
        vo.setTotalSubmit(problem.getTotalSubmit());
        vo.setTotalAccept(problem.getTotalAccept());
        vo.setAcceptRate(problem.getTotalSubmit() > 0
                ? Math.round(problem.getTotalAccept() * 10000.0 / problem.getTotalSubmit()) / 100.0
                : 0.0);

        vo.setScoreDistribution(fillScoreDistribution(
                submissionMapper.selectScoreDistribution(problem.getId())));

        String startDate = LocalDate.now().minusDays(29).toString();
        vo.setSubmissionTrend(fillSubmissionTrend(
                submissionMapper.selectSubmissionTrend(problem.getId(), startDate)));

        return vo;
    }

    private List<ProblemStatisticsVO.ScoreDistributionItem> fillScoreDistribution(
            List<ProblemStatisticsVO.ScoreDistributionItem> raw) {
        String[] ranges = {"0-10", "11-20", "21-30", "31-40", "41-50",
                           "51-60", "61-70", "71-80", "81-90", "91-100"};
        Map<String, Integer> countMap = new HashMap<>();
        if (raw != null) {
            for (var item : raw) {
                countMap.put(item.getRange(), item.getCount());
            }
        }
        List<ProblemStatisticsVO.ScoreDistributionItem> result = new ArrayList<>();
        for (String range : ranges) {
            ProblemStatisticsVO.ScoreDistributionItem item = new ProblemStatisticsVO.ScoreDistributionItem();
            item.setRange(range);
            item.setCount(countMap.getOrDefault(range, 0));
            result.add(item);
        }
        return result;
    }

    private List<ProblemStatisticsVO.SubmissionTrendItem> fillSubmissionTrend(
            List<ProblemStatisticsVO.SubmissionTrendItem> raw) {
        Map<String, Integer> countMap = new HashMap<>();
        if (raw != null) {
            for (var item : raw) {
                countMap.put(item.getDate(), item.getCount());
            }
        }
        List<ProblemStatisticsVO.SubmissionTrendItem> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            ProblemStatisticsVO.SubmissionTrendItem item = new ProblemStatisticsVO.SubmissionTrendItem();
            item.setDate(date);
            item.setCount(countMap.getOrDefault(date, 0));
            result.add(item);
        }
        return result;
    }
}
