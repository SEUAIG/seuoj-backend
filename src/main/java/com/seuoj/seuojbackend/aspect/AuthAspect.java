package com.seuoj.seuojbackend.aspect;

import com.seuoj.seuojbackend.annotation.AllowAnonymous;
import com.seuoj.seuojbackend.annotation.RequireRole;
import com.seuoj.seuojbackend.common.ErrorCode;
import com.seuoj.seuojbackend.common.RoleType;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.interceptor.UserContextHolder;
import com.seuoj.seuojbackend.service.UserRoleService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class AuthAspect {

    private final UserRoleService userRoleService;

    public AuthAspect(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @Around("execution(public * com.seuoj.seuojbackend.controller.api..*(..))")
    public Object authAround(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        Class<?> targetClass = pjp.getTarget().getClass();
        Method method = AopUtils.getMostSpecificMethod(ms.getMethod(), targetClass);
        String className = targetClass.getSimpleName();
        String methodName = method.getName();

        log.debug("开始权限校验 - Controller.api: {}.{}", className, methodName);

        if (hasAllowAnonymous(method, targetClass)) {
            log.info("方法允许匿名访问，跳过权限校验 - {}.{}", className, methodName);
            return pjp.proceed();
        }

        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            log.warn("用户上下文为空，拒绝访问 - {}.{}", className, methodName);
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }
        if (ctx.isGuest()) {
            log.warn("游客尝试访问需要登录资源，拒绝访问 - {}.{}", className, methodName);
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }

        log.debug("用户已登录 - userId: {}, {}.{}", ctx.getUserId(), className, methodName);

        RequireRole requireRole = AnnotationUtils.findAnnotation(method, RequireRole.class);
        if (requireRole == null) {
            requireRole = AnnotationUtils.findAnnotation(targetClass, RequireRole.class);
        }

        if (requireRole != null) {
            log.debug(
                    "检测到角色要求，开始角色校验 - 需要角色: {}, {}.{}",
                    Arrays.toString(requireRole.value()),
                    className,
                    methodName
            );
            checkRole(requireRole);
        } else {
            log.debug("无角色要求，仅需登录 - {}.{}", className, methodName);
        }

        log.debug("权限校验通过，执行业务方法 - {}.{}", className, methodName);
        Object result = pjp.proceed();
        log.debug("业务方法执行完成 - {}.{}", className, methodName);
        return result;
    }

    public void checkRole(RequireRole requireRole) {
        Long userId = UserContextHolder.get().getUserId();
        log.debug("开始角色校验 - userId: {}", userId);

        if (userId == null) {
            log.error("角色校验失败：userId 为空（游客）");
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }

        log.debug("查询用户角色 - userId: {}", userId);
        List<String> roleCodesByUserId = userRoleService.getRoleCodesByUserId(userId);
        log.debug("查询到用户角色列表 - userId: {}, roles: {}", userId, roleCodesByUserId);

        if (roleCodesByUserId.isEmpty()) {
            if (requireRole.value().length == 0) {
                log.info("用户无角色且无角色要求，放行 - userId: {}", userId);
                return;
            }
            log.warn(
                    "用户无角色但存在角色要求 - userId: {}, 所需角色: {}",
                    userId,
                    Arrays.toString(requireRole.value())
            );
            throw new ForbiddenException("用户角色不足，无权访问");
        }

        log.debug(
                "开始匹配用户角色 - userId: {}, 用户角色: {}, 所需角色: {}",
                userId,
                roleCodesByUserId,
                Arrays.toString(requireRole.value())
        );

        for (RoleType roleReq : requireRole.value()) {
            if (roleCodesByUserId.contains(roleReq.getCode())) {
                log.info(
                        "角色校验通过 - userId: {}, 用户角色: {}, 命中角色: {}",
                        userId,
                        roleCodesByUserId,
                        roleReq.getCode()
                );
                return;
            }
        }

        log.warn(
                "角色校验失败 - userId: {}, 用户角色: {}, 所需角色: {}",
                userId,
                roleCodesByUserId,
                Arrays.toString(requireRole.value())
        );
        throw new ForbiddenException("用户角色不足，无权访问");
    }

    private boolean hasAllowAnonymous(Method method, Class<?> targetClass) {
        AllowAnonymous onMethod = AnnotationUtils.findAnnotation(method, AllowAnonymous.class);
        if (onMethod != null) {
            log.debug(
                    "方法上找到 @AllowAnonymous 注解 - {}.{}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName()
            );
            return true;
        }

        AllowAnonymous onClass = AnnotationUtils.findAnnotation(targetClass, AllowAnonymous.class);
        if (onClass != null) {
            log.debug("类上找到 @AllowAnonymous 注解 - {}", targetClass.getSimpleName());
            return true;
        }

        return false;
    }
}
