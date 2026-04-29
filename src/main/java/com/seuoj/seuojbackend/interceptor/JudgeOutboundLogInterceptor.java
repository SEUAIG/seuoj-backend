package com.seuoj.seuojbackend.interceptor;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class JudgeOutboundLogInterceptor implements ClientHttpRequestInterceptor {

    private final String judgeServerUrl;

    public JudgeOutboundLogInterceptor(@Value("${judge.server-url}") String judgeServerUrl) {
        this.judgeServerUrl = judgeServerUrl;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        URI uri = request.getURI();
        String url = uri.toString();
        if (!StringUtils.hasText(judgeServerUrl) || !url.startsWith(judgeServerUrl)) {
            return execution.execute(request, body);
        }

        long startNanos = System.nanoTime();
        ClientHttpResponse response = execution.execute(request, body);
        long costMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        int statusCode = response.getStatusCode().value();
        String className = defaultIfBlank(MDC.get("judgeCallerClass"), "UnknownClass");
        String methodName = defaultIfBlank(MDC.get("judgeCallerMethod"), "unknown");

        log.info(
                "调用评测端 | 方法={}.{} | HTTP={} {} | 状态码={} | 耗时={}ms",
                className,
                methodName,
                request.getMethod(),
                uri.getRawPath(),
                statusCode,
                costMs
        );
        return response;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
