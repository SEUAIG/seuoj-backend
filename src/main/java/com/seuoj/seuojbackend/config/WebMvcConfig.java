package com.seuoj.seuojbackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.interceptor.JudgeAuthInterceptor;
import com.seuoj.seuojbackend.interceptor.JwtAuthInterceptor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final JudgeAuthInterceptor judgeAuthInterceptor;
    private final ObjectMapper objectMapper;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor, JudgeAuthInterceptor judgeAuthInterceptor,
                        ObjectMapper objectMapper) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.judgeAuthInterceptor = judgeAuthInterceptor;
        this.objectMapper = objectMapper;
    }

    /**
     * 注册过滤器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**");

        registry.addInterceptor(judgeAuthInterceptor)
                .addPathPatterns("/judge/**");
    }

    /**
     * 配置 JSON 响应强制使用 UTF-8 编码，避免中文乱码
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        converter.setSupportedMediaTypes(List.of(
                new MediaType("application", "json", StandardCharsets.UTF_8),
                new MediaType("application", "*+json", StandardCharsets.UTF_8)
        ));
        // 添加到最前面，优先使用
        converters.addFirst(converter);
    }
}
