package com.seuoj.seuojbackend.client;

import com.seuoj.seuojbackend.client.dto.*;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.exception.JudgeRemoteException;
import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
    public JudgeOnlineSubmissionResponseData submitOnline(JudgeOnlineSubmissionRequest request) {
        String url = judgeServerUrl + "/judge/submission/online";
        log.info("发送在线评测请求: submissionId={}, pid={}", request.getSubmissionId(), request.getPid());
        try {
            ResponseEntity<Result<JudgeOnlineSubmissionResponseData>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<JudgeOnlineSubmissionResponseData> body = response.getBody();
            if (body == null || !Integer.valueOf(0).equals(body.getCode()) || body.getData() == null) {
                throw new JudgeRemoteException("在线评测端异常: " + body);
            }
            return body.getData();
        } catch (RestClientException ex) {
            log.warn("在线评测端未返回200ok, 路径: {}", url, ex);
            throw new JudgeRemoteException("无法向评测端提交在线评测", ex);
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
    public ProblemConfigDTO fetchProblemConfig(String pid) {
        String url = judgeServerUrl + "/judge/problem/config/" + pid;
        log.info("请求评测端题目配置, pid={}, url={}", pid, url);
        try {
            ResponseEntity<Result<ProblemConfigDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<ProblemConfigDTO> body = response.getBody();
            if (body != null && Integer.valueOf(0).equals(body.getCode()) && body.getData() != null) {
                return body.getData();
            }

            log.error("获取题目配置失败, pid={}, body={}", pid, body);
            throw new JudgeRemoteException("获取题目配置失败");
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                throw new JudgeRemoteException("评测端未找到该题目", ex);
            }
            throw new JudgeRemoteException("无法向评测端获取题目配置", ex);
        } catch (RestClientException ex) {
            throw new JudgeRemoteException("无法向评测端获取题目配置", ex);
        }
    }

    @Override
    public void deleteProblem(String pid) {
        String url = judgeServerUrl + "/judge/problem/" + pid;
        log.info("请求评测端删除题目, pid={}, url={}", pid, url);
        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    new HttpEntity<>(buildHeaders()),
                    Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new JudgeRemoteException("删除题目失败");
            }
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                throw new JudgeRemoteException("评测端未找到该题目", ex);
            }
            throw new JudgeRemoteException("无法向评测端删除题目", ex);
        } catch (RestClientException ex) {
            throw new JudgeRemoteException("无法向评测端删除题目", ex);
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
    public Object fetchProblemTree(String pid) {
        String url = judgeServerUrl + "/judge/problem/tree/" + pid;
        log.info("向评测端请求题目文件树, pid={}, url={}", pid, url);
        try {
            ResponseEntity<Result<java.util.Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<java.util.Map<String, Object>> body = response.getBody();
            if (body != null && Integer.valueOf(0).equals(body.getCode()) && body.getData() != null) {
                Object tree = body.getData().get("tree");
                if (tree != null) {
                    log.info("成功获取题目文件树, pid={}", pid);
                    return tree;
                }
            }

            log.error("获取题目文件树失败, pid={}, body={}", pid, body);
            throw new JudgeRemoteException("获取题目文件树失败");
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                log.warn("评测端未找到题目, pid={}, url={}", pid, url);
                throw new JudgeRemoteException("评测端未找到该题目", ex);
            }
            log.warn("评测端返回了非200ok, status={}, url={}", statusCode, url, ex);
            throw new JudgeRemoteException("无法向评测端获取题目文件树", ex);
        } catch (RestClientException ex) {
            log.warn("评测端未返回200ok, 路径: {}", url, ex);
            throw new JudgeRemoteException("无法向评测端获取题目文件树", ex);
        }
    }

    @Override
    public byte[] fetchProblemFile(String pid, String fileName) {
        String url = judgeServerUrl + "/judge/problem/file/" + pid + "/" + fileName;
        log.info("向评测端请求题目文件, pid={}, fileName={}, url={}", pid, fileName, url);
        try {
            HttpHeaders headers = new HttpHeaders();
            if (judgeSecret != null && !judgeSecret.isEmpty()) {
                headers.set(SECRET_HEADER, judgeSecret);
            }
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("成功获取题目文件, pid={}, fileName={}, size={}", pid, fileName, response.getBody().length);
                return response.getBody();
            }

            log.error("获取题目文件失败, pid={}, fileName={}", pid, fileName);
            throw new JudgeRemoteException("获取题目文件失败");
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                log.warn("评测端未找到文件, pid={}, fileName={}", pid, fileName);
                throw new JudgeRemoteException("评测端未找到该文件", ex);
            }
            log.warn("评测端返回了非200ok, status={}, pid={}, fileName={}", statusCode, pid, fileName, ex);
            throw new JudgeRemoteException("无法向评测端获取题目文件", ex);
        } catch (RestClientException ex) {
            log.warn("评测端请求失败, pid={}, fileName={}", pid, fileName, ex);
            throw new JudgeRemoteException("无法向评测端获取题目文件", ex);
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
