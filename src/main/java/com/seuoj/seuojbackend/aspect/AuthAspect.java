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

        if (hasAllowAnonymous(method, targetClass)) {
            return pjp.proceed();
        }

        UserContext ctx = UserContextHolder.get();
        if (ctx == null || ctx.isGuest()) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }

        RequireRole requireRole = AnnotationUtils.findAnnotation(method, RequireRole.class);
        if (requireRole == null) {
            requireRole = AnnotationUtils.findAnnotation(targetClass, RequireRole.class);
        }

        if (requireRole != null) {
            log.debug(
                    "权限校验: needRoles={}, method={}.{}",
                    Arrays.toString(requireRole.value()),
                    className,
                    methodName
            );
            checkRole(requireRole);
        }

        return pjp.proceed();
    }

    public void checkRole(RequireRole requireRole) {
        Long userId = UserContextHolder.get().getUserId();
        if (userId == null) {
            throw new AuthorizationException(ErrorCode.NOT_LOGIN_ERROR.getCode(), "未登录");
        }

        List<String> roleCodesByUserId = userRoleService.getRoleCodesByUserId(userId);
        if (roleCodesByUserId.isEmpty()) {
            if (requireRole.value().length == 0) {
                return;
            }
            throw new ForbiddenException("用户角色不足，无权访问");
        }

        for (RoleType roleReq : requireRole.value()) {
            if (roleCodesByUserId.contains(roleReq.getCode())) {
                return;
            }
        }

        throw new ForbiddenException("用户角色不足，无权访问");
    }

    private boolean hasAllowAnonymous(Method method, Class<?> targetClass) {
        AllowAnonymous onMethod = AnnotationUtils.findAnnotation(method, AllowAnonymous.class);
        if (onMethod != null) {
            return true;
        }
        AllowAnonymous onClass = AnnotationUtils.findAnnotation(targetClass, AllowAnonymous.class);
        return onClass != null;
    }
}
