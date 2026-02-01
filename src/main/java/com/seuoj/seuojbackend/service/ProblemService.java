package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import com.seuoj.seuojbackend.dto.problem.ProblemPageDTO;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import com.seuoj.seuojbackend.vo.problem.ProblemListItemVO;
import com.seuoj.seuojbackend.vo.problem.ProblemPageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProblemService {

    private final ProblemMapper problemMapper;
    private final JudgeClient judgeClient;

    public ProblemService(ProblemMapper problemMapper, JudgeClient judgeClient) {
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
    }

    /**
     * 分页查询题目列表
     *
     * @param dto 分页查询参数
     * @return 分页结果
     */
    public ProblemPageVO getProblemPage(ProblemPageDTO dto) {
        List<Long> tagIds = dto.getTagIds();
        int tagIdsSize = (tagIds == null) ? 0 : tagIds.size();

        Page<ProblemListItemVO> page = new Page<>(dto.getCurrent(), dto.getSize());
        IPage<ProblemListItemVO> resultPage = problemMapper.selectProblemPage(page, dto.getTitle(), tagIds, tagIdsSize);

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
        vo.setCurrent(dto.getCurrent());
        vo.setSize(dto.getSize());
        vo.setTotal(resultPage.getTotal());
        vo.setRecords(records != null ? records : Collections.emptyList());

        return vo;
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
            log.warn("获取题目详情时发现题目 {} 不存在", pid);
            throw new NotFoundException("题目不存在");
        }

        List<String> tags = problemMapper.getProblemTags(pid);
        problemDetail.setTags(tags != null ? tags : Collections.emptyList());

        ProblemContentDTO problemContentDTO = judgeClient.fetchProblemContent(pid);
        if (problemContentDTO == null) {
            log.warn("题目 {} 的内容在判题服务中缺失", pid);
            throw new NotFoundException("题目不存在");
        }

        problemDetail.setContent(problemContentDTO);
        return problemDetail;
    }
}
