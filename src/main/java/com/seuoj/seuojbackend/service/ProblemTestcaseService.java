package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeProblemDataResponse;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

@Slf4j
@Service
public class ProblemTestcaseService {

    private static final String SECRET_HEADER = "X-Judge-Secret";

    private final ProblemService problemService;
    private final JudgeClient judgeClient;
    private final RestTemplate restTemplate;

    @Value("${judge.server-url}")
    private String judgeServerUrl;

    @Value("${judge.secret:}")
    private String judgeSecret;

    public ProblemTestcaseService(ProblemService problemService, JudgeClient judgeClient, RestTemplate restTemplate) {
        this.problemService = problemService;
        this.judgeClient = judgeClient;
        this.restTemplate = restTemplate;
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

    private void validateFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BadRequestException("文件名不能为空");
        }
        if (fileName.contains("..") || fileName.contains("\\") || fileName.contains(":")) {
            throw new BadRequestException("文件名包含非法字符");
        }
    }

    private void ensureProblemExists(String pid) {
        problemService.getProblemByPidOrThrow(pid);
    }

    /**
     * 代理 /api/problem 下 data/config/file 相关接口到评测端。
     * 转发规则：请求路径仅将前缀 /api/ 替换为 /judge/。
     */
    public void proxyJudgeProblemApi(String pid, String fileName, HttpServletRequest request, HttpServletResponse response) {
        validatePid(pid);
        if (fileName != null) {
            validateFileName(fileName);
        }
        ensureProblemExists(pid);

        String requestPath = request.getRequestURI();
        if (!requestPath.startsWith("/api/")) {
            throw new BadRequestException("不支持的代理路径");
        }

        String judgePath = requestPath.replaceFirst("^/api/", "/judge/");
        String query = request.getQueryString();
        String judgeUrl = judgeServerUrl + judgePath + (StringUtils.hasText(query) ? "?" + query : "");

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        final String contentType = request.getContentType();
        final boolean isMultipart = StringUtils.hasText(contentType)
                && contentType.toLowerCase().startsWith("multipart/form-data");

        try {
            restTemplate.execute(
                    judgeUrl,
                    method,
                    clientHttpRequest -> {
                        HttpHeaders outboundHeaders = clientHttpRequest.getHeaders();
                        if (StringUtils.hasText(judgeSecret)) {
                            outboundHeaders.set(SECRET_HEADER, judgeSecret);
                        }

                        if (isMultipart) {
                            streamMultipartBody(request, outboundHeaders, clientHttpRequest.getBody());
                        } else {
                            if (StringUtils.hasText(contentType)) {
                                outboundHeaders.set(HttpHeaders.CONTENT_TYPE, contentType);
                            }
                            try (InputStream in = request.getInputStream(); OutputStream out = clientHttpRequest.getBody()) {
                                StreamUtils.copy(in, out);
                            }
                        }
                    },
                    clientHttpResponse -> {
                        writeProxyResponse(response, clientHttpResponse);
                        return null;
                    }
            );
        } catch (HttpStatusCodeException ex) {
            writeProxyResponse(response, ex.getStatusCode().value(), ex.getResponseHeaders(), ex.getResponseBodyAsByteArray());
        } catch (ResourceAccessException ex) {
            throw new JudgeRemoteException("评测端请求失败", ex);
        } catch (RestClientException ex) {
            throw new JudgeRemoteException("评测端请求失败", ex);
        }
    }

    private void streamMultipartBody(HttpServletRequest request, HttpHeaders outboundHeaders, OutputStream out) {
        String boundary = "----seuoj-" + UUID.randomUUID();
        outboundHeaders.set(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);

        Collection<Part> parts;
        try {
            parts = request.getParts();
        } catch (IOException | ServletException e) {
            throw new BadRequestException("读取 multipart 请求失败");
        }

        try {
            byte[] lineBreak = "\r\n".getBytes(StandardCharsets.UTF_8);
            for (Part part : parts) {
                out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(buildContentDisposition(part).getBytes(StandardCharsets.UTF_8));
                out.write(lineBreak);

                String partContentType = part.getContentType();
                if (StringUtils.hasText(partContentType)) {
                    out.write(("Content-Type: " + partContentType + "\r\n").getBytes(StandardCharsets.UTF_8));
                }
                out.write(lineBreak);

                try (InputStream partIn = part.getInputStream()) {
                    StreamUtils.copy(partIn, out);
                }
                out.write(lineBreak);
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new JudgeRemoteException("转发 multipart 请求失败", e);
        }
    }

    private String buildContentDisposition(Part part) {
        StringBuilder sb = new StringBuilder("Content-Disposition: form-data; name=\"")
                .append(escapeQuotes(part.getName()))
                .append("\"");

        String submittedFileName = part.getSubmittedFileName();
        if (StringUtils.hasText(submittedFileName)) {
            sb.append("; filename=\"").append(escapeQuotes(submittedFileName)).append("\"");
        }
        return sb.toString();
    }

    private String escapeQuotes(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private void writeProxyResponse(HttpServletResponse response, org.springframework.http.client.ClientHttpResponse judgeResponse) {
        try {
            response.setStatus(judgeResponse.getStatusCode().value());
            HttpHeaders headers = judgeResponse.getHeaders();
            copyResponseHeaders(response, headers);
            try (InputStream in = judgeResponse.getBody(); OutputStream out = response.getOutputStream()) {
                if (in != null) {
                    StreamUtils.copy(in, out);
                    out.flush();
                }
            }
        } catch (IOException e) {
            log.error("写入代理响应失败", e);
            throw new RuntimeException("响应写入失败", e);
        }
    }

    private void writeProxyResponse(HttpServletResponse response, int status, HttpHeaders headers, byte[] body) {
        response.setStatus(status);
        copyResponseHeaders(response, headers);

        if (body == null || body.length == 0) {
            return;
        }

        response.setContentLength(body.length);
        try {
            response.getOutputStream().write(body);
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("写入代理响应失败", e);
            throw new RuntimeException("响应写入失败", e);
        }
    }

    private void copyResponseHeaders(HttpServletResponse response, HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        if (StringUtils.hasText(contentType)) {
            response.setContentType(contentType);
        }
        String contentDisposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (StringUtils.hasText(contentDisposition)) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        }
        String cacheControl = headers.getFirst(HttpHeaders.CACHE_CONTROL);
        if (StringUtils.hasText(cacheControl)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, cacheControl);
        }
    }

    /**
     * 获取题目测试点元信息
     *
     * @param pid 题目编号
     * @return 测试点元信息
     */
    @Deprecated
    public JudgeProblemDataResponse getProblemTestcaseMeta(String pid) {
        ensureProblemExists(pid);
        return judgeClient.fetchProblemDataMeta(pid);
    }

    /**
     * 获取题目文件树（直接代理到评测端）
     *
     * @param pid 题目编号
     * @return 文件树数据
     */
    public Object getProblemTree(String pid) {
        log.info("获取题目文件树, pid={}", pid);
        validatePid(pid);
        ensureProblemExists(pid);
        return judgeClient.fetchProblemTree(pid);
    }
}
