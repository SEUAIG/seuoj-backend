package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeProblemDataRequest;
import com.seuoj.seuojbackend.client.dto.JudgeProblemDataResponse;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.archive.ProblemArchiveExtractor;
import com.seuoj.seuojbackend.vo.problem.ProblemTestcaseMetaVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class ProblemTestcaseService {

    private final ProblemMapper problemMapper;
    private final JudgeClient judgeClient;
    private final ProblemArchiveExtractor archiveExtractor;

    public ProblemTestcaseService(ProblemMapper problemMapper, JudgeClient judgeClient,
                                  ProblemArchiveExtractor archiveExtractor) {
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
        this.archiveExtractor = archiveExtractor;
    }

    /**
     * 上传题目数据
     *
     * @param pid      题目编号
     * @param file     测试数据文件
     * @param format   文件格式
     * @param nameRule 测试数据命名规则
     */
    public void uploadProblemTestcases(String pid, MultipartFile file, String format, String nameRule) {
        log.info("开始上传题目数据, pid={}, format={}, fileName={}", pid, format,
                file != null ? file.getOriginalFilename() : null);
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("上传题目数据时发现题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("测试数据文件不能为空");
        }

        if (!StringUtils.hasText(format)) {
            throw new BadRequestException("文件格式不能为空");
        }

        String normalizedFormat = archiveExtractor.normalizeArchiveFormat(format);
        if (normalizedFormat == null) {
            throw new BadRequestException("不支持的文件格式，仅支持 zip/tar/tar.gz/tgz/7z");
        }

        long archiveSize = file.getSize();
        if (archiveSize <= 0) {
            throw new BadRequestException("测试数据文件为空");
        }

        archiveExtractor.validateArchiveSize(archiveSize);
        archiveExtractor.validateArchiveFormat(file, normalizedFormat);

        List<ProblemArchiveExtractor.NameRuleItem> nameRules = archiveExtractor.parseNameRule(nameRule);
        log.info("解析测试数据命名规则完成, pid={}, count={}", pid, nameRules.size());

        Set<String> expectedNames = new HashSet<>();
        for (ProblemArchiveExtractor.NameRuleItem rule : nameRules) {
            expectedNames.add(rule.getInputName());
            expectedNames.add(rule.getAnswerName());
        }

        Map<String, byte[]> fileContents = archiveExtractor.readExpectedEntries(file, normalizedFormat, expectedNames);
        log.info("读取压缩包完成, pid={}, fileCount={}", pid, fileContents.size());

        List<JudgeProblemDataRequest.TestcaseItem> testcaseItems = new ArrayList<>();
        for (ProblemArchiveExtractor.NameRuleItem rule : nameRules) {
            byte[] inputBytes = fileContents.get(rule.getInputName());
            if (inputBytes == null) {
                throw new BadRequestException("压缩包缺少输入文件: " + rule.getInputName());
            }

            byte[] answerBytes = fileContents.get(rule.getAnswerName());
            if (answerBytes == null) {
                throw new BadRequestException("压缩包缺少输出文件: " + rule.getAnswerName());
            }

            JudgeProblemDataRequest.TestcaseItem item = new JudgeProblemDataRequest.TestcaseItem();
            item.setId(rule.getId());
            item.setInput(archiveExtractor.decodeUtf8Text(inputBytes, rule.getInputName()));
            item.setInputName(rule.getInputName());
            item.setAnswer(archiveExtractor.decodeUtf8Text(answerBytes, rule.getAnswerName()));
            item.setAnswerName(rule.getAnswerName());
            testcaseItems.add(item);
        }
        log.info("组装测试数据完成, pid={}, testcaseCount={}", pid, testcaseItems.size());

        JudgeProblemDataRequest request = new JudgeProblemDataRequest();
        request.setPid(pid);
        request.setTestcase(testcaseItems);
        judgeClient.uploadProblemData(request);
        log.info("上传题目数据结束, pid={}", pid);
    }

    /**
     * 获取题目测试点元信息
     *
     * @param pid 题目编号
     * @return 测试点元信息
     */
    public List<ProblemTestcaseMetaVO> getProblemTestcaseMeta(String pid) {
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("获取题目测试点元信息时发现题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        List<JudgeProblemDataResponse.TestcaseMeta> testcases = judgeClient.fetchProblemDataMeta(pid);
        if (testcases == null || testcases.isEmpty()) {
            return List.of();
        }

        return testcases.stream()
                .map(item -> {
                    ProblemTestcaseMetaVO vo = new ProblemTestcaseMetaVO();
                    vo.setId(item.getId());
                    vo.setInputName(item.getInputName());
                    vo.setAnswerName(item.getAnswerName());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 透传题目文件
     *
     * @param pid      题目编号
     * @param fileName 文件名
     * @param response 响应流
     */
    public void proxyProblemFile(String pid, String fileName, HttpServletResponse response) {
        if (!StringUtils.hasText(pid) || !StringUtils.hasText(fileName)) {
            throw new BadRequestException("参数不能为空");
        }
        if (containsUnsafePathSegment(pid) || containsUnsafePathSegment(fileName)) {
            throw new BadRequestException("参数包含非法字符");
        }
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("获取题目文件时发现题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }
        judgeClient.proxyProblemFile(pid, fileName, response);
    }

    private boolean containsUnsafePathSegment(String value) {
        return value.contains("/") || value.contains("\\")
                || value.contains("..") || value.contains(":");
    }
}
