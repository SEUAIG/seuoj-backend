package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeProblemEditRequest;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import com.seuoj.seuojbackend.dto.problem.ProblemEditDTO;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.ProblemTagRel;
import com.seuoj.seuojbackend.entity.Tag;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemTagRelMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.TagMapper;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
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

    public ProblemService(ProblemMapper problemMapper, JudgeClient judgeClient, TagMapper tagMapper,
                          ProblemTagRelMapper problemTagRelMapper) {
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
        this.tagMapper = tagMapper;
        this.problemTagRelMapper = problemTagRelMapper;
    }

    /**
     * 根据 pid 获取题目详情
     *
     * @param pid 题目编号
     * @return 题目详情 VO
     */
    public ProblemDetailVO getProblemDetail(String pid) {
        ProblemDetailVO problemDetail = problemMapper.getProblemDetail(pid);
        if (problemDetail == null) {
            log.warn("获取题目详情时发现题目{} 不存在", pid);
            throw new NotFoundException("题目不存在");
        }

        List<String> tags = problemMapper.getProblemTags(pid);
        problemDetail.setTags(tags != null ? tags : Collections.emptyList());

        ProblemContentDTO problemContentDTO = judgeClient.fetchProblemContent(pid);
        if (problemContentDTO == null) {
            log.warn("题目 {} 的内容在评测服务中缺失", pid);
            throw new NotFoundException("题目不存在");
        }

        problemDetail.setContent(problemContentDTO);
        return problemDetail;
    }

    /**
     * 编辑题面
     *
     * @param dto 编辑请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void editProblem(ProblemEditDTO dto) {
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, dto.getPid()));
        if (problem == null) {
            log.warn("编辑题面时发现题目不存在, pid={}", dto.getPid());
            throw new NotFoundException("题目不存在");
        }

        updateProblemMeta(problem, dto);
        judgeClient.updateProblem(buildJudgeRequest(dto));
    }

    private void updateProblemMeta(Problem problem, ProblemEditDTO dto) {
        if (StringUtils.hasText(dto.getTitle())) {
            problemMapper.update(null, new LambdaUpdateWrapper<Problem>()
                    .set(Problem::getTitle, dto.getTitle())
                    .eq(Problem::getId, problem.getId()));
        }

        if (dto.getTags() != null) {
            updateProblemTags(problem.getId(), dto.getTags());
        }
    }

    private void updateProblemTags(Long problemId, List<Long> tags) {
        Set<Long> tagIds = tags.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        problemTagRelMapper.delete(new LambdaQueryWrapper<ProblemTagRel>()
                .eq(ProblemTagRel::getProblemId, problemId));

        if (tagIds.isEmpty()) {
            return;
        }

        List<Tag> existingTags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .in(Tag::getId, tagIds));
        if (existingTags.size() != tagIds.size()) {
            log.warn("更新题目标签时存在无效标签, problemId={}, tagIds={}", problemId, tagIds);
            throw new BadRequestException("标签不存在");
        }

        for (Tag tag : existingTags) {
            ProblemTagRel rel = new ProblemTagRel();
            rel.setProblemId(problemId);
            rel.setTagId(tag.getId());
            problemTagRelMapper.insert(rel);
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

        if (dto.getInfo() != null) {
            request.setInfo(dto.getInfo());
        }

        if (dto.getInteractor() != null) {
            request.setInteractor(dto.getInteractor());
        }

        if (dto.getChecker() != null) {
            request.setChecker(dto.getChecker());
        }

        return request;
    }
}
