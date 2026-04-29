package com.seuoj.seuojbackend.config;

import java.net.http.HttpClient;
import java.util.List;
import com.seuoj.seuojbackend.interceptor.JudgeOutboundLogInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(JudgeOutboundLogInterceptor judgeOutboundLogInterceptor) {
        // 使用 JDK HttpClient 以支持 PATCH 等方法
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.setInterceptors(List.of(judgeOutboundLogInterceptor));
        return restTemplate;
    }
}
