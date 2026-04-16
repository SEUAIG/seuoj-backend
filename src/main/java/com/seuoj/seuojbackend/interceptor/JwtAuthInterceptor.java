package com.seuoj.seuojbackend.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.common.AuthStatus;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.mapper.UserInfoMapper;
import com.seuoj.seuojbackend.util.JwtTokenType;
import com.seuoj.seuojbackend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 拦截器，解析请求jwt，构建用户上下文，存入线程上下文
 */
@Slf4j
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    private final JwtUtil jwtUtil;
    private final UserInfoMapper userInfoMapper;

    public JwtAuthInterceptor(JwtUtil jwtUtil, UserInfoMapper userInfoMapper) {
        this.jwtUtil = jwtUtil;
        this.userInfoMapper = userInfoMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, @NonNull HttpServletResponse resp, @NonNull Object handler) {
        String requestUri = req.getRequestURI();
        String method = req.getMethod();
        log.debug("开始处理请求: {} {}", method, requestUri);

        // 非 Controller 方法（比如静态资源）直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            log.info("非 Controller 方法，跳过认证 - URI: {}", requestUri);
            UserContextHolder.set(UserContext.guest());
            return true;
        }

        String controllerName = handlerMethod.getBeanType().getSimpleName();
        String methodName = handlerMethod.getMethod().getName();
        log.debug("处理 Controller 方法: {}.{}", controllerName, methodName);

        String token = resolveBearerToken(req);

        // 没 token：游客
        if (token == null) {
            log.info("请求未携带 token，作为游客处理 - URI: {} {}, Controller及方法: {}.{}",
                    method, requestUri, controllerName, methodName);
            UserContextHolder.set(UserContext.guest());
            return true;
        }

        // 有令牌则进行解析与校验
        log.debug("开始解析 JWT 令牌");
        JwtUtil.ParsedToken parsedToken = jwtUtil.parseToken(token);
        if (parsedToken == null || parsedToken.tokenType() != JwtTokenType.ACCESS) {
            log.warn("JWT 令牌无效或不是访问令牌 - URI: {} {}, Controller: {}.{}",
                    method, requestUri, controllerName, methodName);
            UserContextHolder.set(UserContext.of(null, AuthStatus.INVALID_TOKEN));
            return true;
        }

        UserInfo user = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
                .eq(UserInfo::getPublicId, parsedToken.subject()));
        if (user == null) {
            log.warn("JWT 令牌中的用户标识不存在 - publicId: {}, URI: {} {}, Controller: {}.{}",
                    parsedToken.subject(), method, requestUri, controllerName, methodName);
            UserContextHolder.set(UserContext.of(null, AuthStatus.INVALID_TOKEN));
            return true;
        }

        log.info("JWT 鉴权成功 - userId: {}, URI: {} {}, Controller: {}.{}",
                user.getId(), method, requestUri, controllerName, methodName);
        UserContextHolder.set(UserContext.of(user.getId(), AuthStatus.AUTHENTICATED));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, @NonNull HttpServletResponse resp, @NonNull Object handler, Exception ex) {
        // 请求完成后，清理线程上下文
        String requestUri = req.getRequestURI();
        int statusCode = resp.getStatus();

        if (ex != null) {
            log.error("请求处理异常 - URI: {}, Status: {}, Exception: {}",
                    requestUri, statusCode, ex.getMessage(), ex);
        } else {
            log.debug("请求处理完成 - URI: {}, Status: {}", requestUri, statusCode);
        }

        UserContextHolder.clear();
        log.debug("已清理线程上下文 - URI: {}", requestUri);
    }

    private String resolveBearerToken(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth == null) {
            log.debug("请求头中未找到 Authorization 字段");
            return null;
        }

        auth = auth.trim();
        if (!auth.startsWith("Bearer ")) {
            log.warn("Authorization 字段格式不正确，不是 Bearer token 格式: {}", auth.substring(0, Math.min(20, auth.length())));
            return null;
        }

        String token = auth.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            log.warn("Bearer token 为空");
            return null;
        }

        log.debug("成功提取 Bearer token，长度: {}", token.length());
        return token;
    }
}
