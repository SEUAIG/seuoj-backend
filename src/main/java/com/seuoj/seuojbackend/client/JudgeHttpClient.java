package com.seuoj.seuojbackend.client;

import com.seuoj.seuojbackend.client.dto.JudgeProblemDataRequest;
import com.seuoj.seuojbackend.client.dto.JudgeProblemDataResponse;
import com.seuoj.seuojbackend.client.dto.JudgeProblemEditRequest;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 评测端 HTTP 请求发送统一层
 */
@Slf4j
@Service
public class JudgeHttpClient implements JudgeClient {

    private static final String SECRET_HEADER = "X-Judge-Secret";

    @Resource
    private RestTemplate restTemplate;

    @Value("${judge.server-url}")
    private String judgeServerUrl;

    @Value("${judge.secret:}")
    private String judgeSecret;

    @Override
    public ProblemContentDTO fetchProblemContent(String pid) {
        String url = judgeServerUrl + "/judge/problem/" + pid;
        log.info("向评测端请求题目详细信息, pid={}, url={}", pid, url);
        try {
            ResponseEntity<Result<ProblemContentDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<ProblemContentDTO> body = response.getBody();
            if (body != null && Integer.valueOf(0).equals(body.getCode()) && body.getData() != null) {
                log.info("成功获取题目详细信息, pid={}", pid);
                return body.getData();
            }

            // TODO: 更具体详细的错误码处理
            log.error("获取题目详细信息失败，异常响应详情: {}", body);
            throw new JudgeRemoteException("获取题目详细信息失败");
        } catch (RestClientException ex) {
            throw new JudgeRemoteException("获取题目详细信息失败", ex);
        }
    }

    @Override
    public void submit(JudgeSubmissionRequest request) {
        String url = judgeServerUrl + "/judge/submission";
        log.info("发送提交测评请求: submissionNo={}, pid={}", request.getSubmissionId(), request.getPid());
        try {
            ResponseEntity<Result<Void>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<Void> body = response.getBody();
            if (body == null || !Integer.valueOf(0).equals(body.getCode())) {
                // TODO: 更具体详细的错误码处理
                throw new JudgeRemoteException("评测端异常: " + body);
            }

            log.info("成功发送提交评测请求, submissionNo={}", request.getSubmissionId());
        } catch (RestClientException ex) {
            log.warn("评测端未返回200ok, 路径: {}", url, ex);
            throw new JudgeRemoteException("无法向评测端提交", ex);
        }
    }

    @Override
    public void updateProblem(JudgeProblemEditRequest request) {
        String url = judgeServerUrl + "/judge/problem/edit";
        log.info("向评测端请求更新题目信息, pid={}, url={}", request.getPid(), url);
        try {
            ResponseEntity<Result<Void>> response = restTemplate.exchange(
                    url,
                    HttpMethod.PATCH,
                    new HttpEntity<>(request, buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<Void> body = response.getBody();
            if (body == null || !Integer.valueOf(0).equals(body.getCode())) {
                log.error("更新题目信息失败，异常响应详情: {}", body);
                throw new JudgeRemoteException("更新题目信息失败");
            }

            log.info("成功更新题目信息, pid={}", request.getPid());
        } catch (RestClientException ex) {
            log.warn("评测端未返回200ok, 路径: {}", url, ex);
            throw new JudgeRemoteException("无法向评测端更新题目信息", ex);
        }
    }

    @Override
    public void uploadProblemData(JudgeProblemDataRequest request) {
        String url = judgeServerUrl + "/judge/problem/data";
        log.info("向评测端上传题目数据, pid={}, url={}", request.getPid(), url);
        try {
            ResponseEntity<Result<Void>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<Void> body = response.getBody();
            if (body == null || !Integer.valueOf(0).equals(body.getCode())) {
                log.error("上传题目数据失败，异常响应详情: {}", body);
                throw new JudgeRemoteException("上传题目数据失败");
            }

            log.info("成功上传题目数据, pid={}", request.getPid());
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                log.warn("评测端未找到题目, pid={}, url={}", request.getPid(), url);
                throw new JudgeRemoteException("评测端未找到该题目", ex);
            }
            if (statusCode == 400) {
                log.warn("上传题目数据参数错误, pid={}, url={}", request.getPid(), url);
                throw new JudgeRemoteException("上传题目数据参数错误", ex);
            }
            log.warn("评测端返回了非200ok, status={}, url={}", statusCode, url, ex);
            throw new JudgeRemoteException("无法向评测端上传题目数据", ex);
        } catch (RestClientException ex) {

            log.warn("评测端未返回200ok, 路径: {}", url, ex);
            throw new JudgeRemoteException("无法向评测端上传题目数据", ex);
        }
    }

    @Override
    public JudgeProblemDataResponse fetchProblemDataMeta(String pid) {
        String url = judgeServerUrl + "/judge/problem/data/" + pid;
        log.info("向评测端请求题目测试点元信息, pid={}, url={}", pid, url);
        try {
            ResponseEntity<Result<JudgeProblemDataResponse>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<JudgeProblemDataResponse> body = response.getBody();
            if (body != null && Integer.valueOf(0).equals(body.getCode())
                    && body.getData() != null && body.getData().getTestCases() != null) {
                log.info("成功获取题目测试点元信息, pid={}, count={}", pid, body.getData().getTestCases().size());
                return body.getData();
            }

            log.error("获取题目测试点元信息失败, pid={}, body={}", pid, body);
            throw new JudgeRemoteException("获取题目测试点元信息失败");
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                log.warn("评测端未找到题目, pid={}, url={}", pid, url);
                throw new JudgeRemoteException("评测端未找到该题目", ex);
            }
            log.warn("评测端返回了非200ok, status={}, url={}", statusCode, url, ex);
            throw new JudgeRemoteException("无法向评测端获取题目测试点元信息", ex);
        } catch (RestClientException ex) {
            log.warn("评测端未返回200ok, 路径: {}", url, ex);
            throw new JudgeRemoteException("无法向评测端获取题目测试点元信息", ex);
        }
    }

    @Override
    public void proxyProblemFile(String pid, String fileName, HttpServletResponse response) {
        String url = UriComponentsBuilder.fromUriString(judgeServerUrl)
                .pathSegment("judge", "problem", "file", pid, fileName)
                .build()
                .toUriString();
        log.info("向评测端请求题目文件, pid={}, fileName={}, url={}", pid, fileName, url);
        try {
            restTemplate.execute(url, HttpMethod.GET, request -> {
                if (judgeSecret != null && !judgeSecret.isEmpty()) {
                    request.getHeaders().set(SECRET_HEADER, judgeSecret);
                }
            }, clientResponse -> {
                response.setStatus(clientResponse.getStatusCode().value());
                HttpHeaders headers = clientResponse.getHeaders();
                MediaType contentType = headers.getContentType();
                if (contentType != null) {
                    response.setContentType(contentType.toString());
                }
                long contentLength = headers.getContentLength();
                if (contentLength >= 0) {
                    response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
                }
                String disposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
                if (disposition != null) {
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
                }
                try (var body = clientResponse.getBody()) {
                    StreamUtils.copy(body, response.getOutputStream());
                }
                response.flushBuffer();
                return null;
            });
        } catch (HttpStatusCodeException ex) {
            handleProxyError(ex, response);
        } catch (RestClientException ex) {
            log.warn("评测端请求题目文件失败, pid={}, fileName={}, url={}", pid, fileName, url, ex);
            throw new JudgeRemoteException("无法向评测端获取题目文件", ex);
        }
    }

    private void handleProxyError(HttpStatusCodeException ex, HttpServletResponse response) {
        try {
            response.setStatus(ex.getStatusCode().value());
            HttpHeaders headers = ex.getResponseHeaders();
            if (headers != null) {
                MediaType contentType = headers.getContentType();
                if (contentType != null) {
                    response.setContentType(contentType.toString());
                }
                String disposition = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
                if (disposition != null) {
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
                }
            }
            byte[] body = ex.getResponseBodyAsByteArray();
            if (body.length > 0) {
                StreamUtils.copy(body, response.getOutputStream());
            }
            response.flushBuffer();
        } catch (IOException ioEx) {
            throw new JudgeRemoteException("透传评测端响应失败", ioEx);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (judgeSecret != null && !judgeSecret.isEmpty()) {
            headers.set(SECRET_HEADER, judgeSecret);
        }
        return headers;
    }
}
