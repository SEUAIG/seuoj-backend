package com.seuoj.seuojbackend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.ProblemTagRel;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.ProblemTagRelMapper;
import com.seuoj.seuojbackend.mapper.TagMapper;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProblemService {

    @Resource
    private ProblemMapper problemMapper;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private ProblemTagRelMapper problemTagRelMapper;

    @Resource
    private RestTemplate restTemplate;

    @Value("${judge.server-url}")
    private String judgeServerUrl;

    /**
     * 从 pid 获取题目详情
     * 
     * @param pid 题目编号
     * @return 题目详情 VO
     */
    public ProblemDetailVO getProblemDetail(String pid) {
        Problem problem = problemMapper.selectById(pid);
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
        vo.setContent(fetchContentFromJudgeServer(problem.getPid()));

        // 计算通过率
        if (problem.getTotalSubmit() > 0) {
            double rate = (double) problem.getTotalAccept() / problem.getTotalSubmit() * 100;
            vo.setAcceptRate(String.format("%.2f%%", rate));
        } else {
            vo.setAcceptRate("暂无提交");
        }

        return vo;
    }

    /**
     * 从评测端获取题目详细内容
     */
    @SuppressWarnings("unchecked")
    private String fetchContentFromJudgeServer(String pid) {
        String url = judgeServerUrl + "/judge/problem/" + pid;
        log.info("请求评测端获取题目内容: {}", url);

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && Integer.valueOf(0).equals(response.get("code"))) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null && data.get("content") != null) {
                    log.info("成功获取题目内容 - pid: {}", pid);
                    return (String) data.get("content");
                }
            }
            log.warn("评测端返回数据格式异常 - pid: {}, response: {}", pid, response);
            return "题目内容获取失败";
        } catch (Exception e) {
            log.error("评测端连接失败 - pid: {}, error: {}", pid, e.getMessage());
            return "评测端连接失败: " + e.getMessage();
        }
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
