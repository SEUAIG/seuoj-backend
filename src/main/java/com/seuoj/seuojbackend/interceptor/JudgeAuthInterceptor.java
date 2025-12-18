package com.seuoj.seuojbackend.interceptor;

import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.common.Result;
import com.seuoj.seuojbackend.exception.JudgeAuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 测评服务接口拦截器
 * 用于校验其是否是可靠来源（初步实现）
 */
@Component
public class JudgeAuthInterceptor implements HandlerInterceptor {

    @Value("${judge.secret}")
    private String judgeSecret;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String secret = request.getHeader("X-Judge-Secret");

        if (secret == null || !secret.equals(judgeSecret)) {
            throw new JudgeAuthException("未知的评测端服务");
        }

        return true;
    }
}