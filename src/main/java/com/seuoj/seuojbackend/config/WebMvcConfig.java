package com.seuoj.seuojbackend.config;

import com.seuoj.seuojbackend.interceptor.JudgeAuthInterceptor;
import com.seuoj.seuojbackend.interceptor.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final JudgeAuthInterceptor judgeAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor, JudgeAuthInterceptor judgeAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.judgeAuthInterceptor = judgeAuthInterceptor;
    }

    /**
     * 注册过滤器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**");

//        registry.addInterceptor(judgeAuthInterceptor)
//                .addPathPatterns("/judge/**");
    }
}