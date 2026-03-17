package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeProblemDataResponse;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ProblemTestcaseService {

    private final ProblemMapper problemMapper;
    private final JudgeClient judgeClient;

    public ProblemTestcaseService(ProblemMapper problemMapper, JudgeClient judgeClient) {
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
    }

    /**
     * 校验 pid 参数合法性，禁止特殊字符
     */
    private void validatePid(String pid) {
        if (!StringUtils.hasText(pid)) {
            throw new BadRequestException("pid 不能为空");
        }
        if (pid.contains("/") || pid.contains("\\") || pid.contains("..") || pid.contains(":")) {
            throw new BadRequestException("pid 包含非法字符");
        }
    }

    /**
     * 上传题目测试数据（X-Accel-Redirect 重定向版本）
     * 仅校验题目是否存在，然后返回 X-Accel-Redirect 头由 nginx 转发到评测端
     *
     * @param pid      题目编号
     * @param response HTTP 响应
     */
    public void redirectTestcaseUpload(String pid, HttpServletResponse response) {
        log.info("测试数据上传重定向, pid={}", pid);
        validatePid(pid);
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("上传题目数据时发现题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        response.setHeader("X-Accel-Redirect",
                "/internal/judgend/judge/problem/data/" + pid);
        response.setStatus(HttpServletResponse.SC_OK);
        log.info("已设置 X-Accel-Redirect 头, pid={}", pid);
    }

    private static final java.util.Set<String> VALID_CONFIG_TYPES = java.util.Set.of("META", "CASE");

    /**
     * 获取题目配置
     * 仅校验题目是否存在和参数合法性，然后返回 X-Accel-Redirect 头由 nginx 转发到评测端
     *
     * @param pid      题目编号
     * @param type     配置类型：META 或 CASE
     * @param response HTTP 响应
     */
    public void redirectProblemConfig(String pid, String type, HttpServletResponse response) {
        log.info("题目配置重定向, pid={}, type={}", pid, type);
        validatePid(pid);

        if (!StringUtils.hasText(type) || !VALID_CONFIG_TYPES.contains(type.toUpperCase())) {
            throw new BadRequestException("type 参数无效，仅支持 META 或 CASE");
        }

        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("获取题目配置时发现题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        response.setHeader("X-Accel-Redirect",
                "/internal/judgend/judge/problem/config/" + pid + "?type=" + type.toUpperCase());
        response.setStatus(HttpServletResponse.SC_OK);
        log.info("已设置 X-Accel-Redirect 头(config), pid={}, type={}", pid, type);
    }

    /**
     * 获取题目测试点元信息
     *
     * @param pid 题目编号
     * @return 测试点元信息
     */
    @Deprecated
    public JudgeProblemDataResponse getProblemTestcaseMeta(String pid) {
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("获取题目测试点元信息时发现题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        return judgeClient.fetchProblemDataMeta(pid);
    }

    /**
     * 获取题目文件
     * 仅校验参数合法性和题目存在性，然后返回 X-Accel-Redirect 头由 nginx 转发到评测端
     * file_name 支持子目录路径（如 subtask1/1.in），但不允许路径穿越（..）
     *
     * @param pid      题目编号
     * @param fileName 文件名（可含子目录）
     * @param response HTTP 响应
     */
    public void redirectProblemFile(String pid, String fileName, HttpServletResponse response) {
        log.info("题目文件重定向, pid={}, fileName={}", pid, fileName);
        validatePid(pid);

        if (!StringUtils.hasText(fileName)) {
            throw new BadRequestException("文件名不能为空");
        }
        // fileName 允许 / （子目录）但不允许路径穿越
        if (fileName.contains("..") || fileName.contains("\\") || fileName.contains(":")) {
            throw new BadRequestException("文件名包含非法字符");
        }
        // 去除 Spring wildcard 匹配可能带来的前导斜杠
        String cleanFileName = fileName.startsWith("/") ? fileName.substring(1) : fileName;
        if (!StringUtils.hasText(cleanFileName)) {
            throw new BadRequestException("文件名不能为空");
        }

        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("获取题目文件时发现题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        response.setHeader("X-Accel-Redirect",
                "/internal/judgend/judge/problem/file/" + pid + "/" + cleanFileName);
        response.setStatus(HttpServletResponse.SC_OK);
        log.info("已设置 X-Accel-Redirect 头(file), pid={}, fileName={}", pid, cleanFileName);
    }

    /**
     * 获取题目文件树（直接代理到评测端）
     * <p>
     * 当前实现：后端通过 RestTemplate 直接请求评测端并返回结果，适用于开发环境。
     * <p>
     * 生产环境 Nginx 方案（X-Accel-Redirect）：
     * <pre>
     *   // 后端仅设置响应头，由 Nginx 内部重定向到评测端：
     *   response.setHeader("X-Accel-Redirect",
     *           "/internal/judgend/judge/problem/tree/" + pid);
     *   response.setStatus(HttpServletResponse.SC_OK);
     *
     *   // Nginx 配置示例：
     *   // location /internal/judgend/ {
     *   //     internal;
     *   //     proxy_pass http://judgend-upstream/;
     *   // }
     * </pre>
     *
     * @param pid 题目编号
     * @return 文件树数据
     */
    public Object getProblemTree(String pid) {
        log.info("获取题目文件树, pid={}", pid);
        validatePid(pid);
        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("获取题目文件树时发现题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        return judgeClient.fetchProblemTree(pid);
    }

    /**
     * 获取题目文件（直接代理到评测端，返回字节流）
     * <p>
     * 当前实现：后端通过 RestTemplate 请求评测端获取文件字节数组，写入 response 返回给前端。
     * <p>
     * 生产环境 Nginx 方案（X-Accel-Redirect）：
     * <pre>
     *   // 后端仅设置响应头，由 Nginx 内部重定向到评测端：
     *   response.setHeader("X-Accel-Redirect",
     *           "/internal/judgend/judge/problem/file/" + pid + "/" + cleanFileName);
     *   response.setStatus(HttpServletResponse.SC_OK);
     *
     *   // Nginx 配置示例：
     *   // location /internal/judgend/ {
     *   //     internal;
     *   //     proxy_pass http://judgend-upstream/;
     *   // }
     * </pre>
     *
     * @param pid      题目编号
     * @param fileName 文件路径
     * @param response HTTP 响应
     */
    public void proxyProblemFile(String pid, String fileName, HttpServletResponse response) {
        log.info("代理获取题目文件, pid={}, fileName={}", pid, fileName);
        validatePid(pid);

        if (!org.springframework.util.StringUtils.hasText(fileName)) {
            throw new BadRequestException("文件名不能为空");
        }
        if (fileName.contains("..") || fileName.contains("\\") || fileName.contains(":")) {
            throw new BadRequestException("文件名包含非法字符");
        }
        String cleanFileName = fileName.startsWith("/") ? fileName.substring(1) : fileName;
        if (!org.springframework.util.StringUtils.hasText(cleanFileName)) {
            throw new BadRequestException("文件名不能为空");
        }

        Problem problem = problemMapper.selectOne(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getPid, pid));
        if (problem == null) {
            log.warn("获取题目文件时发现题目不存在, pid={}", pid);
            throw new NotFoundException("题目不存在");
        }

        byte[] data = judgeClient.fetchProblemFile(pid, cleanFileName);

        // 推断 Content-Type
        String contentType = "application/octet-stream";
        if (cleanFileName.endsWith(".in") || cleanFileName.endsWith(".out") || cleanFileName.endsWith(".ans") || cleanFileName.endsWith(".txt")) {
            contentType = "text/plain; charset=utf-8";
        }

        response.setContentType(contentType);
        response.setContentLength(data.length);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + cleanFileName.substring(cleanFileName.lastIndexOf('/') + 1) + "\"");
        try {
            response.getOutputStream().write(data);
            response.getOutputStream().flush();
        } catch (java.io.IOException e) {
            log.error("写入文件响应失败, pid={}, fileName={}", pid, cleanFileName, e);
            throw new RuntimeException("文件传输失败", e);
        }
    }
}
