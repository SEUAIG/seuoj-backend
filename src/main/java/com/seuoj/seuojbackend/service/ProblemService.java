package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeProblemEditRequest;
import com.seuoj.seuojbackend.client.dto.ProblemConfigDTO;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import com.seuoj.seuojbackend.common.ProblemCommon;
import com.seuoj.seuojbackend.dto.problem.ProblemEditDTO;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.ProblemTagRel;
import com.seuoj.seuojbackend.entity.Tag;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.ProblemTagRelMapper;
import com.seuoj.seuojbackend.mapper.TagMapper;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import com.seuoj.seuojbackend.vo.problem.ProblemListItemVO;
import com.seuoj.seuojbackend.vo.problem.ProblemPageVO;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ProblemService {

    private final ProblemMapper problemMapper;
    private final JudgeClient judgeClient;
    private final TagMapper tagMapper;
    private final ProblemTagRelMapper problemTagRelMapper;

    public ProblemService(ProblemMapper problemMapper, JudgeClient judgeClient, TagMapper tagMapper,
                          ProblemTagRelMapper problemTagRelMapper) {
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
        this.tagMapper = tagMapper;
        this.problemTagRelMapper = problemTagRelMapper;
    }

    /**
     * 分页查询题目列表
     */
    // TODO: 考虑查询功能使用更优雅的实现？比如 Redis Search？代价是系统整体复杂度提升？
    public ProblemPageVO getProblemPage(Integer current, Integer size, String title, List<Long> tagIds) {
        if (tagIds != null && !tagIds.isEmpty()) {
            tagIds = tagIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }
        int tagIdsSize = (tagIds == null) ? 0 : tagIds.size();

        SearchSpec searchSpec = SearchSpec.from(title);

        Page<ProblemListItemVO> page = new Page<>(current, size);
        IPage<ProblemListItemVO> resultPage = problemMapper.selectProblemPage(
                page,
                searchSpec.fulltextQuery,
                searchSpec.titleLike,
                searchSpec.useFulltext,
                searchSpec.singleTokens,
                searchSpec.useLikeSingle,
                searchSpec.useLikeRaw,
                tagIds,
                tagIdsSize
        );

        // 批量获取标签
        List<ProblemListItemVO> records = resultPage.getRecords();
        if (records != null && !records.isEmpty()) {
            List<String> pids = records.stream()
                    .map(ProblemListItemVO::getPid)
                    .collect(Collectors.toList());

            // 批量查询标签
            List<ProblemMapper.ProblemTagResult> tagResults = problemMapper.getProblemTagsBatch(pids);

            // 按 pid 分组
            Map<String, List<String>> pidTagsMap = new HashMap<>();
            for (ProblemMapper.ProblemTagResult tagResult : tagResults) {
                pidTagsMap.computeIfAbsent(tagResult.getPid(), k -> new ArrayList<>())
                        .add(tagResult.getTagName());
            }

            // 设置标签
            for (ProblemListItemVO item : records) {
                item.setTags(pidTagsMap.getOrDefault(item.getPid(), Collections.emptyList()));
            }
        }

        // 构建返回结果
        ProblemPageVO vo = new ProblemPageVO();
        vo.setCurrent(current);
        vo.setSize(size);
        vo.setTotal(resultPage.getTotal());
        vo.setRecords(records != null ? records : Collections.emptyList());

        return vo;
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
        int mode = 0; // 0 none, 1 cjk, 2 alnum

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

    /**
     * 根据 pid 获取题目详情
     */
    public ProblemDetailVO getProblemDetail(String pid) {
        // 从数据库获取题目元信息
        ProblemDetailVO problemDetail = problemMapper.getProblemDetail(pid);
        if (problemDetail == null) {
            log.warn("获取题目详情时题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        List<String> tags = problemMapper.getProblemTags(pid);
        problemDetail.setTags(tags != null ? tags : Collections.emptyList());

        // 从评测段获取题目详情
        ProblemContentDTO problemContentDTO = judgeClient.fetchProblemContent(pid);
        if (problemContentDTO == null) {
            log.warn("题目 {} 的内容在评测服务中缺失", pid);
            throw new NotFoundException("题目不存在");
        }

        problemDetail.setContent(problemContentDTO);

        // 从评测端获取题目配置数据
        ProblemConfigDTO problemConfigDTO = judgeClient.fetchProblemConfig(pid);
        fillProblemContentByProblemConfig(problemContentDTO, problemConfigDTO);
        return problemDetail;
    }

    // TODO: 会不会有并发修改的风险？
    /**
     * 编辑题面
     */
    @Transactional(rollbackFor = Exception.class)
    public void editProblem(ProblemEditDTO dto) {
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, dto.getPid()));
        if (problem == null) {
            log.warn("编辑题面时题目不存在, pid={}", dto.getPid());
            throw new NotFoundException("题目不存在");
        }

        updateProblemMeta(problem.getId(), dto.getTitle(), dto.getIsPublic());
        if (dto.getTags() != null) {
            updateProblemTags(problem.getId(), dto.getTags());
        }
        judgeClient.updateProblem(buildJudgeRequest(dto));
    }

    /**
     * 删除题目
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProblem(String pid) {
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("删除题目时题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        // 校验
        validateProblemDeleteRules(problem.getId(), pid);


        // 删数据库
        problemTagRelMapper.markAllDeletedByProblemId(problem.getId());
        problemMapper.deleteById(problem.getId());

        // 删评测端数据
        judgeClient.deleteProblem(pid);
    }

    /**
     * 校验题目是否可以删除（是否存在关联记录）
     *
     * @param problemId 题目主键id
     * @param pid       题目pid
     */
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

    /**
     * 更新题目元数据
     *
     * @param problemId 题目主键id
     * @param title     标题
     * @param isPublic  是否公开
     */
    private void updateProblemMeta(Long problemId, String title, Boolean isPublic) {
        Problem problem = new Problem();
        problem.setId(problemId);
        problem.setTitle(title);
        if (isPublic != null) {
            problem.setIsPublic(isPublic ? 1 : 0);
        }
        problemMapper.updateById(problem);
    }

    /**
     * 更新题目标签信息
     *
     * @param problemId 题目主键id
     * @param tags      标签主键id列表
     */
    private void updateProblemTags(Long problemId, List<Long> tags) {
        // 去重去null清洗
        Set<Long> tagIds = tags.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (tagIds.isEmpty()) {
            problemTagRelMapper.markAllDeletedByProblemId(problemId);
            return;
        }

        // 验证标签存在性
        List<Tag> existingTags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .in(Tag::getId, tagIds));
        if (existingTags.size() != tagIds.size()) {
            log.warn("更新题目标签时存在无效标签, problemId={}, tagIds={}", problemId, tagIds);
            throw new BadRequestException("标签不存在");
        }

        // 移除所有标签关联
        problemTagRelMapper.markAllDeletedByProblemId(problemId);

        // 恢复标签关联
        List<Long> existingRelTagIds = problemTagRelMapper
                .selectTagIdsByProblemIdAndTagIds(problemId, tagIds);
        Set<Long> existingRelTagIdSet = new LinkedHashSet<>(existingRelTagIds);
        if (!existingRelTagIdSet.isEmpty()) {
            problemTagRelMapper.restoreByProblemIdAndTagIds(problemId, existingRelTagIdSet);
        }

        // 新增标签关联
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
        JudgeProblemEditRequest request = new JudgeProblemEditRequest();
        request.setPid(dto.getPid());
        request.setDescription(dto.getDescription());
        request.setInput(dto.getInput());
        request.setOutput(dto.getOutput());

        if (dto.getExample() != null) {
            List<com.seuoj.seuojbackend.common.ProblemCommon.Example> examples = dto.getExample().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            request.setExample(examples);
        }

        request.setHint(dto.getHint());

        return request;
    }

    /**
     * 用题目配置填充题目详情info
     */
    private void fillProblemContentByProblemConfig(ProblemContentDTO problemContent, ProblemConfigDTO problemConfig) {
        if (problemConfig == null) {
            return;
        }

        long defaultTimeLimitMs = 1000L;
        long defaultMemoryLimitKb = 256L * 1024;

        if (problemConfig.getProblemInfo() != null) {
            if (problemConfig.getProblemInfo().getTimeLimitMs() != null) {
                defaultTimeLimitMs = problemConfig.getProblemInfo().getTimeLimitMs();
            }
            if (problemConfig.getProblemInfo().getMemoryLimitKb() != null) {
                defaultMemoryLimitKb = problemConfig.getProblemInfo().getMemoryLimitKb();
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
        info.setMinMemoryByte(minMemoryLimitKb);
        info.setMaxMemoryByte(maxMemoryLimitKb);

        info.setProblemType(problemConfig.getProblemInfo().getProblemType());
        info.setCheckerType(problemConfig.getProblemInfo().getCheckerType());
    }
}
