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
        try {
            ResponseEntity<Result<ProblemContentDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<ProblemContentDTO> body = response.getBody();
            if (body != null && Integer.valueOf(0).equals(body.getCode()) && body.getData() != null) {
                return body.getData();
            }

            // 后续可按评测端错误码细化异常类型
            log.error("获取题目详细信息失败，异常响应详情: {}", body);
            throw new JudgeRemoteException("获取题目详细信息失败");
        } catch (RestClientException ex) {
            throw new JudgeRemoteException("获取题目详细信息失败", ex);
        }
    }

    @Override
    public void submit(JudgeSubmissionRequest request) {
        String url = judgeServerUrl + "/judge/submission";
        try {
            ResponseEntity<Result<Void>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<Void> body = response.getBody();
            if (body == null || !Integer.valueOf(0).equals(body.getCode())) {
                // 后续可按评测端错误码细化异常类型
                throw new JudgeRemoteException("评测端异常: " + body);
            }

        } catch (RestClientException ex) {
            throw new JudgeRemoteException("无法向评测端提交", ex);
        }
    }

    @Override
    public JudgeOnlineSubmissionResponseData submitOnline(JudgeOnlineSubmissionRequest request) {
        String url = judgeServerUrl + "/judge/submission/online";
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
            throw new JudgeRemoteException("无法向评测端提交在线评测", ex);
        }
    }

    @Override
    public JudgeLanguagesResponseData fetchLanguages() {
        String url = judgeServerUrl + "/judge/languages";
        try {
            ResponseEntity<Result<JudgeLanguagesResponseData>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    new ParameterizedTypeReference<>() {
                    });

            Result<JudgeLanguagesResponseData> body = response.getBody();
            if (body == null || !Integer.valueOf(0).equals(body.getCode()) || body.getData() == null) {
                throw new JudgeRemoteException("获取评测语言失败: " + body);
            }
            return body.getData();
        } catch (RestClientException ex) {
            throw new JudgeRemoteException("无法从评测端获取可用语言", ex);
        }
    }

    @Override
    public void updateProblem(JudgeProblemEditRequest request) {
        String url = judgeServerUrl + "/judge/problem/edit";
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

        } catch (RestClientException ex) {
            throw new JudgeRemoteException("无法向评测端更新题目信息", ex);
        }
    }

    @Override
    public ProblemConfigDTO fetchProblemConfig(String pid) {
        String url = judgeServerUrl + "/judge/problem/config/" + pid;
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
                return body.getData();
            }

            log.error("获取题目测试点元信息失败, pid={}, body={}", pid, body);
            throw new JudgeRemoteException("获取题目测试点元信息失败");
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                throw new JudgeRemoteException("评测端未找到该题目", ex);
            }
            throw new JudgeRemoteException("无法向评测端获取题目测试点元信息", ex);
        } catch (RestClientException ex) {
            throw new JudgeRemoteException("无法向评测端获取题目测试点元信息", ex);
        }
    }

    @Override
    public Object fetchProblemTree(String pid) {
        String url = judgeServerUrl + "/judge/problem/tree/" + pid;
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
                    return tree;
                }
            }

            log.error("获取题目文件树失败, pid={}, body={}", pid, body);
            throw new JudgeRemoteException("获取题目文件树失败");
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                throw new JudgeRemoteException("评测端未找到该题目", ex);
            }
            throw new JudgeRemoteException("无法向评测端获取题目文件树", ex);
        } catch (RestClientException ex) {
            throw new JudgeRemoteException("无法向评测端获取题目文件树", ex);
        }
    }

    @Override
    public byte[] fetchProblemFile(String pid, String fileName) {
        String url = judgeServerUrl + "/judge/problem/file/" + pid + "/" + fileName;
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
                return response.getBody();
            }

            log.error("获取题目文件失败, pid={}, fileName={}", pid, fileName);
            throw new JudgeRemoteException("获取题目文件失败");
        } catch (HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                throw new JudgeRemoteException("评测端未找到该文件", ex);
            }
            throw new JudgeRemoteException("无法向评测端获取题目文件", ex);
        } catch (RestClientException ex) {
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
