package com.seuoj.seuojbackend.service;

import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.ProblemTagRel;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.ProblemTagRelMapper;
import com.seuoj.seuojbackend.mapper.TagMapper;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProblemService {

    private final ProblemMapper problemMapper;
    private final TagMapper tagMapper;
    private final ProblemTagRelMapper problemTagRelMapper;
    private final JudgeClient judgeClient;

    public ProblemService(
            ProblemMapper problemMapper,
            TagMapper tagMapper,
            ProblemTagRelMapper problemTagRelMapper,
            JudgeClient judgeClient) {
        this.problemMapper = problemMapper;
        this.tagMapper = tagMapper;
        this.problemTagRelMapper = problemTagRelMapper;
        this.judgeClient = judgeClient;
    }

    /**
     * 从 pid 获取题目详情
     *
     * @param pid 题目编号
     * @return 题目详情 VO
     */
    public ProblemDetailVO getProblemDetail(String pid) {
        Problem problem = problemMapper.selectOne(new  QueryWrapper<Problem>().eq("pid", pid));
        if (problem == null) {
            throw new NotFoundException("题目不存在: " + pid);
        }
        return convertToVO(problem);
    }

    /**
     * Entity 转 VO
     */
    private ProblemDetailVO convertToVO(Problem problem) {
        ProblemDetailVO vo = new ProblemDetailVO();
        vo.setPid(problem.getPid());
        vo.setTitle(problem.getTitle());
        vo.setTotalSubmit(problem.getTotalSubmit());
        vo.setTotalAccept(problem.getTotalAccept());
        vo.setCreatedAt(problem.getCreatedAt());
        vo.setTags(getTagsByProblemId(problem.getId()));

        // 从评测端获取 content
        vo.setContent(judgeClient.fetchProblemContent(problem.getPid()).getContent());

        // 计算通过率
        if (problem.getTotalSubmit() > 0) {
            double rate = (double) problem.getTotalAccept() / problem.getTotalSubmit() * 100;
            vo.setAcceptRate(String.format("%.2f%%", rate));
        } else {
            vo.setAcceptRate("暂无提交");
        }

        return vo;
    }

    private List<String> getTagsByProblemId(Long problemId) {
        List<ProblemTagRel> relList = problemTagRelMapper
                .selectList(new QueryWrapper<ProblemTagRel>().eq("problem_id", problemId));
        List<String> tagList = new ArrayList<>();
        for (ProblemTagRel rel : relList) {
            tagList.add(tagMapper.selectById(rel.getTagId()).getTagName());
        }
        return tagList;
    }
}
